NCAT Minecraft

O projeto deriva de WebDisplays, originalmente criado por montoyo e mantido por CinemaMod Group. Base utilizada: https://github.com/CinemaMod/webdisplays, branch 1.20, commit 594c4decf3059841106f8418c424d2362b430968.

O upstream inclui contribuições e avisos históricos de BARBOTIN Nicolas, GiantLuigi4, ds58, Mysticpasta1, montoyo, WaterPicker e CinemaMod Group. Os cabeçalhos de copyright existentes nos arquivos de origem permanecem preservados. A licença MIT original está no arquivo LICENSE e também é incluída no JAR.

As alterações específicas do NCAT Minecraft são de autoria da Netcat Test. Esta atribuição não substitui a autoria histórica do WebDisplays.

MCEF/Chromium é uma dependência separada. Seu código e licença não são incorporados por este projeto.

O NCAT SSH inclui JSch 2.28.7, de mwiede e colaboradores, como biblioteca interna do JAR. Suas licenças Revised BSD e ISC estão no JAR da biblioteca.

A ponte local do VNC usa o Netty 4.1.82 (netty-codec-http e os módulos de que ele depende), do The Netty Project, sob licença Apache 2.0. Esses módulos são embutidos no JAR em META-INF/jarjar.

O terminal usa xterm.js 5.5.0 e @xterm/addon-fit 0.10.0, ambos sob licença MIT. As licenças estão em assets/ncat_minecraft/html/vendor/xterm-LICENSE e addon-fit-LICENSE.

A tela VNC usa noVNC 1.7.0 sob MPL 2.0. Os fontes originais e a licença estão em assets/ncat_minecraft/html/vendor/novnc; o bundle clássico é gerado por tools/build_novnc_bundle.mjs.

A tela RDP usa FreeRDP 3.32.1 sob Apache 2.0. Os pacotes nativos Windows x64 e Linux x64 em assets/ncat_minecraft/native/rdp incluem a licença do FreeRDP. O pacote Windows inclui licenças das bibliotecas adicionais; o pacote Linux inclui os avisos de copyright das bibliotecas empacotadas. As pontes JNI são compiladas do código em src/main/native/remote.
