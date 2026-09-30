package com.netcattest.ncatminecraft.client.remote;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrameAggregator;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.util.concurrent.DefaultThreadFactory;
import org.cef.browser.CefBrowser;

import java.net.InetSocketAddress;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class VncTunnelServer {
    private static final int MAX_FRAME_BYTES = 16 * 1024 * 1024;
    private static final Map<CefBrowser, Session> SESSIONS = new ConcurrentHashMap<>();
    private static final SecureRandom RANDOM = new SecureRandom();

    private VncTunnelServer() {
    }

    public static synchronized String open(CefBrowser browser, String host, int port) {
        if (browser == null || host == null || port < 1 || port > 65535)
            throw new IllegalArgumentException("invalidConnection");
        String target = host.trim();
        if (target.startsWith("[") && target.endsWith("]"))
            target = target.substring(1, target.length() - 1);
        if (target.isEmpty() || target.length() > 253 || target.chars().anyMatch(ch ->
                Character.isWhitespace(ch) || Character.isISOControl(ch) || ch == '/' || ch == '\\' || ch == '[' || ch == ']'))
            throw new IllegalArgumentException("invalidConnection");

        byte[] secret = new byte[32];
        RANDOM.nextBytes(secret);
        Session session = new Session(browser, target, port, Base64.getUrlEncoder().withoutPadding().encodeToString(secret));
        try {
            String url = session.start();
            Session previous = SESSIONS.put(browser, session);
            if (previous != null)
                previous.close();
            return url;
        } catch (RuntimeException error) {
            session.close();
            throw error;
        }
    }

    public static synchronized void close(CefBrowser browser) {
        if (browser == null)
            return;
        Session session = SESSIONS.remove(browser);
        if (session != null)
            session.close();
    }

    public static synchronized void closeAll() {
        for (Session session : SESSIONS.values())
            session.close();
        SESSIONS.clear();
    }

    private static void expire(Session session) {
        SESSIONS.remove(session.browser, session);
        session.close();
    }

    private static void closeChannel(Channel channel) {
        if (channel != null)
            channel.close();
    }

    private static final class Session {
        private final CefBrowser browser;
        private final String host;
        private final int port;
        private final String token;
        private final NioEventLoopGroup loops = new NioEventLoopGroup(2, new DefaultThreadFactory("NCAT-VNC", true));
        private final AtomicBoolean closed = new AtomicBoolean();
        private volatile Channel listener;
        private volatile Channel webSocket;
        private volatile Channel remote;

        private Session(CefBrowser browser, String host, int port, String token) {
            this.browser = browser;
            this.host = host;
            this.port = port;
            this.token = token;
        }

        private String start() {
            ChannelFuture bound = new ServerBootstrap()
                    .group(loops)
                    .channel(NioServerSocketChannel.class)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel channel) {
                            channel.pipeline().addLast(new HttpServerCodec());
                            channel.pipeline().addLast(new HttpObjectAggregator(16 * 1024));
                            channel.pipeline().addLast(new TokenCheck(Session.this));
                            channel.pipeline().addLast(new WebSocketServerProtocolHandler("/" + token, null, false, MAX_FRAME_BYTES));
                            channel.pipeline().addLast(new WebSocketFrameAggregator(MAX_FRAME_BYTES));
                            channel.pipeline().addLast(new WebSocketRelay(Session.this));
                        }
                    })
                    .bind(new InetSocketAddress("127.0.0.1", 0)).awaitUninterruptibly();
            if (!bound.isSuccess())
                throw new IllegalStateException("vncTunnelUnavailable", bound.cause());
            listener = bound.channel();
            int localPort = ((InetSocketAddress) listener.localAddress()).getPort();
            return "ws://127.0.0.1:" + localPort + "/" + token;
        }

        private boolean connect(Channel webSocket, WebSocketRelay relay) {
            if (closed.get()) {
                webSocket.close();
                return false;
            }
            if (this.webSocket != null) {
                webSocket.close();
                return false;
            }
            this.webSocket = webSocket;
            webSocket.config().setAutoRead(false);
            try {
                new Bootstrap()
                        .group(loops)
                        .channel(NioSocketChannel.class)
                        .option(ChannelOption.TCP_NODELAY, true)
                        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                        .handler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            protected void initChannel(SocketChannel channel) {
                                channel.pipeline().addLast(new TcpRelay(Session.this, webSocket));
                            }
                        })
                        .connect(host, port).addListener((ChannelFuture future) -> {
                            if (!future.isSuccess() || closed.get() || !webSocket.isActive()) {
                                future.channel().close();
                                webSocket.close();
                                return;
                            }
                            remote = future.channel();
                            relay.remote = future.channel();
                            webSocket.config().setAutoRead(true);
                        });
            } catch (RuntimeException error) {
                webSocket.close();
                expire(this);
                return false;
            }
            return true;
        }

        private void close() {
            if (!closed.compareAndSet(false, true))
                return;
            closeChannel(listener);
            closeChannel(webSocket);
            closeChannel(remote);
            loops.shutdownGracefully(0, 2, TimeUnit.SECONDS);
        }
    }

    private static final class TokenCheck extends SimpleChannelInboundHandler<FullHttpRequest> {
        private final Session session;

        private TokenCheck(Session session) {
            this.session = session;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext context, FullHttpRequest request) {
            String origin = request.headers().get(HttpHeaderNames.ORIGIN);
            String upgrade = request.headers().get(HttpHeaderNames.UPGRADE);
            boolean trustedOrigin = origin == null || "null".equals(origin) || "mod://ncat_minecraft".equals(origin);
            boolean accepted = !session.closed.get() && request.decoderResult().isSuccess() &&
                    request.method().name().equals("GET") && request.uri().equals("/" + session.token) &&
                    upgrade != null && HttpHeaderValues.WEBSOCKET.contentEqualsIgnoreCase(upgrade) &&
                    trustedOrigin && context.channel().remoteAddress() instanceof InetSocketAddress address &&
                    address.getAddress() != null && address.getAddress().isLoopbackAddress();
            if (!accepted) {
                DefaultFullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.FORBIDDEN);
                response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, 0);
                context.writeAndFlush(response).addListener(future -> context.close());
                return;
            }
            context.pipeline().remove(this);
            context.fireChannelRead(request.retain());
        }
    }

    private static final class WebSocketRelay extends SimpleChannelInboundHandler<WebSocketFrame> {
        private final Session session;
        private volatile Channel remote;
        private boolean handshaken;

        private WebSocketRelay(Session session) {
            this.session = session;
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext context, Object event) throws Exception {
            if (event instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
                handshaken = session.connect(context.channel(), this);
            }
            super.userEventTriggered(context, event);
        }

        @Override
        protected void channelRead0(ChannelHandlerContext context, WebSocketFrame frame) {
            Channel target = remote;
            if (!(frame instanceof BinaryWebSocketFrame) || target == null || !target.isActive()) {
                context.close();
                return;
            }
            target.writeAndFlush(frame.content().retain());
            if (!target.isWritable())
                context.channel().config().setAutoRead(false);
        }

        @Override
        public void channelWritabilityChanged(ChannelHandlerContext context) throws Exception {
            Channel target = remote;
            if (target != null && target.isActive())
                target.config().setAutoRead(context.channel().isWritable());
            super.channelWritabilityChanged(context);
        }

        @Override
        public void channelInactive(ChannelHandlerContext context) throws Exception {
            if (handshaken)
                expire(session);
            super.channelInactive(context);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext context, Throwable error) {
            context.close();
        }
    }

    private static final class TcpRelay extends SimpleChannelInboundHandler<ByteBuf> {
        private final Session session;
        private final Channel webSocket;

        private TcpRelay(Session session, Channel webSocket) {
            this.session = session;
            this.webSocket = webSocket;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext context, ByteBuf data) {
            if (!webSocket.isActive()) {
                context.close();
                return;
            }
            webSocket.writeAndFlush(new BinaryWebSocketFrame(data.retain()));
            if (!webSocket.isWritable())
                context.channel().config().setAutoRead(false);
        }

        @Override
        public void channelWritabilityChanged(ChannelHandlerContext context) throws Exception {
            if (webSocket.isActive())
                webSocket.config().setAutoRead(context.channel().isWritable());
            super.channelWritabilityChanged(context);
        }

        @Override
        public void channelInactive(ChannelHandlerContext context) throws Exception {
            expire(session);
            super.channelInactive(context);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext context, Throwable error) {
            context.close();
        }
    }
}
