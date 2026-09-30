<p align="center">
  <img src="docs/assets/ncat-minecraft-logo.png" alt="NCAT Minecraft" width="560">
</p>

<h1 align="center">NCAT Minecraft</h1>

<p align="center"><strong>Um laboratório de redes e cibersegurança dentro do Minecraft.</strong></p>

<p align="center">
  <img alt="Minecraft 1.20.1" src="https://img.shields.io/badge/Minecraft-1.20.1-3c8527">
  <img alt="Forge 47.1.65+" src="https://img.shields.io/badge/Forge-47.1.65%2B-1f3a5f">
  <img alt="Java 17" src="https://img.shields.io/badge/Java-17-b07219">
  <img alt="MCEF 2.1.1" src="https://img.shields.io/badge/MCEF-2.1.1-6f42c1">
  <img alt="License MIT" src="https://img.shields.io/badge/license-MIT-blue">
</p>

<p align="center">🇧🇷 <strong>Português</strong> · 🇺🇸 <a href="README.en.md">English</a></p>

---
## Sumário

- [O que é](#o-que-é)
- [Recursos](#recursos)
- [Requisitos](#requisitos)
- [Download e instalação](#download-e-instalação)
- [Primeiros passos](#primeiros-passos)
- [Atalhos do teclado](#atalhos-do-teclado)
- [Abas do modo criativo](#abas-do-modo-criativo)
- [Receitas](#receitas)
- [Configuração](#configuração)
- [Compilar a partir do código-fonte](#compilar-a-partir-do-código-fonte)
- [Estrutura do projeto](#estrutura-do-projeto)
- [Segurança e uso responsável](#segurança-e-uso-responsável)
- [Solução de problemas](#solução-de-problemas)
- [Contribuindo](#contribuindo)
- [Créditos e licenças](#créditos-e-licenças)

## O que é

O **NCAT Minecraft** transforma o Minecraft em um laboratório de redes e cibersegurança. As telas do jogo rodam um navegador Chromium de verdade. A partir delas é possível abrir sessões SSH e SFTP, um terminal local, área de trabalho remota por RDP ou VNC e um proxy que pausa e edita requisições HTTP em tempo real.

A rede também é física: cabos de dados, portas RJ45, switches com VLAN, racks com módulos e uma workstation que alcança tudo o que estiver conectado. O mod foi pensado para aulas e laboratórios autorizados, onde a turma monta a infraestrutura com as próprias mãos e vê o tráfego passando.

## Recursos

### Telas

Cada tela é um bloco que se une aos vizinhos para formar um monitor maior. A cor da moldura identifica a função.

| Tela | Moldura | O que faz |
|---|---|---|
| **Navegador** | vermelha | Chromium completo, com abas, zoom, tela cheia, histórico e barra de endereço. |
| **SSH** | azul | Terminal SSH real (xterm.js), com verificação da impressão digital do servidor. A senha fica só no seu cliente. |
| **SFTP** | ciano | Explorador de arquivos da sessão SSH vinculada: navegar, editar texto UTF-8 até 256 KiB, criar, renomear, excluir e mudar permissões. |
| **Log** | laranja | Registra as requisições HTTP e HTTPS do navegador vinculado. O inspetor laser gera um tablet com os detalhes de cada uma. |
| **Ferramentas de Desenvolvedor** | roxa | Elementos (HTML), console JavaScript e aba de rede do navegador vinculado. |
| **Workstation** | azul-clara | Mostra e controla qualquer tela ou módulo de rack alcançável pela rede de dados. Um rodapé alterna entre eles. |
| **Terminal** | verde-limão | Shell do seu próprio computador: cmd, PowerShell, distribuições WSL, bash, zsh. Roda só no cliente. |
| **Conexão Remota** | verde | Área de trabalho remota por RDP (FreeRDP) ou VNC (noVNC). |
| **Proxy** | magenta | Proxy de interceptação: observa o tráfego, pausa requisições, permite editar método, URL, cabeçalhos e corpo, encaminhar ou rejeitar, e guarda o histórico com os corpos de resposta. |

### Rede física

- **Cabo de dados**, **porta RJ45** e **ferramenta de instalação de portas**: qualquer tela recebe portas de rede. O cabo cai com peso, contorna blocos pelo chão e pode receber pontos de passagem com o **ajustador de cabos**.
- **Switch não gerenciado** de 8 portas e **switch gerenciável** de 8 portas, com VLANs, desativação de portas e isolamento entre portas. O gerenciável é configurado pelo **tablet de configuração**, ligado ao console do switch pelo **cabo serial**.
- **Cabo USB** e **porta USB**, para ligar teclados às telas.
- LEDs de atividade em todas as portas, que piscam com o tráfego real.

### Rack

- **Rack de piso 12 U**, **extensão de 18 U** (empilha até 6 seções) e **rack de parede 6 U**.
- Módulos: navegador, SSH, SFTP, log, ferramentas de desenvolvedor, terminal, conexão remota, proxy, switch e switch gerenciável.
- Porta de vidro, energia por módulo, portas de rede extras e cabos organizados por um canal lateral interno.
- Os módulos não têm tela própria: o conteúdo aparece na workstation conectada pela rede.

### Ferramentas e acessórios

- **Ferramenta de vínculo**: liga um teclado a uma tela, ou uma tela de análise (log, ferramentas de desenvolvedor, proxy, SFTP) à tela de origem.
- **Configurador de tela**: resolução, rotação, dono, amigos e permissões.
- **Apontador laser**, **inspetor laser de log** e **tablet de requisição**.

### Móveis

Cadeira gamer (dá para sentar), vaso sanitário, bebedouro com galão e copo descartável, relógios digitais (hora do mundo e hora local), patinho de borracha e adaptador Wi-Fi.

## Requisitos

| Item | Versão |
|---|---|
| Minecraft Java Edition | 1.20.1 |
| Forge | 47.1.65 ou mais novo, da série 47 |
| Java | 17, 64 bits |
| MCEF | 2.1.1 para Forge 1.20.1 (obrigatório) |
| Sistema | Windows x64 ou Linux x64 |

O RDP só funciona em Windows x64 e Linux x64. As demais telas dependem do suporte do MCEF à sua plataforma. Como cada tela roda um Chromium, reserve pelo menos 4 GB de memória para o Minecraft.

## Download e instalação

1. Instale o **Forge 1.20.1**, versão 47.1.65 ou mais nova, pelo [site oficial do Forge](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.20.1.html).
2. Baixe o **NCAT Minecraft** na página de **[Releases](../../releases/latest)**. Use o arquivo `ncat_minecraft-<versão>.jar`. Se houver um arquivo terminado em `-slim.jar`, **não use**: ele não traz as bibliotecas de SSH e VNC.
3. Baixe o **MCEF 2.1.1** para Forge 1.20.1, no [Modrinth](https://modrinth.com/mod/mcef) ou no [CurseForge](https://www.curseforge.com/minecraft/mc-mods/mcef).
4. Coloque os dois arquivos na pasta `mods` do Minecraft:
   - Windows: `%APPDATA%\.minecraft\mods`
   - Linux: `~/.minecraft/mods`
5. Abra o jogo com o perfil do Forge. Na primeira execução o MCEF baixa os binários do Chromium para `mods/mcef-libraries`, o que pode levar alguns minutos.

**Servidor dedicado:** instale o NCAT Minecraft e o MCEF na pasta `mods` do servidor **e** de todos os jogadores.

**Conferir o download:** cada release traz um arquivo `SHA256SUMS.txt`. Compare com o hash do seu arquivo:

```bash
sha256sum ncat_minecraft-*.jar
```

```powershell
Get-FileHash .\ncat_minecraft-*.jar -Algorithm SHA256
```

## Primeiros passos

### 1. Montar uma tela

Coloque blocos de tela lado a lado, formando um retângulo com pelo menos 2 blocos de largura ou de altura (o máximo padrão é 16 × 16). Clique com o botão direito, de mão vazia, em qualquer bloco: a tela liga.

Se um bloco for quebrado e recolocado em até 10 minutos, a tela volta com as mesmas configurações e portas.

### 2. Digitar

- **Telas de navegador:** coloque um **teclado do navegador** e ligue-o à tela com a **ferramenta de vínculo**: clique primeiro na tela e depois no teclado. Clique no teclado para digitar. Para trocar o endereço, agache e clique com o botão direito na tela.
- **Demais telas** (SSH, SFTP, log, ferramentas de desenvolvedor, workstation, terminal, conexão remota, proxy): agache e clique com o botão direito na tela para digitar direto. Os teclados próprios de cada tela também funcionam.

### 3. Ligar telas de análise

As telas de **log**, **ferramentas de desenvolvedor** e **proxy** analisam um navegador. A de **SFTP** usa uma sessão SSH. Há dois jeitos de ligar:

- **Ferramenta de vínculo:** clique na tela de origem (navegador ou SSH) e depois na tela de análise.
- **Cabo de dados:** conecte as duas diretamente ou pela rede de switches.

### 4. Montar a rede

1. Com a **ferramenta de instalação de portas** e **portas RJ45** no inventário, adicione portas às telas.
2. Segure um **cabo de dados**, clique na porta de um aparelho e depois na porta de outro.
3. Use um **switch** para ligar vários aparelhos. No **switch gerenciável**, conecte o **tablet de configuração** pelo **cabo serial** para criar VLANs, desativar portas ou isolar portas.
4. Com o **ajustador de cabos**, adicione pontos de passagem ou recolha um cabo.

### 5. Usar a workstation

Conecte uma **tela de workstation** à rede. Ela lista todas as telas e módulos de rack alcançáveis, respeitando VLANs e portas desativadas. Selecione um equipamento no rodapé para ver e controlar a tela dele.

### 6. Montar um rack

1. Coloque o **rack de piso 12 U** no chão e empilhe outras seções (até 6).
2. Com um **módulo** na mão, clique na frente do rack. A altura do clique escolhe a unidade.
3. Com a mão vazia, clique para abrir o painel: ligar e desligar módulos, retirar, abrir portas e acrescentar portas de rede.
4. Agache e clique com a mão vazia para abrir ou fechar a porta de vidro.
5. Ligue um cabo de dados entre o rack e a rede da workstation para ver os módulos.

### 7. Usar o proxy

Vincule a tela de **proxy** a um navegador e escolha o modo:

| Modo | Comportamento |
|---|---|
| **Observar** | Só registra o tráfego. O navegador carrega normalmente. É o padrão. |
| **Ativo** | O proxy entra no caminho e guarda requisição e resposta completas. |
| **Interceptar** | Como o ativo, mas pausa cada requisição para você editar e encaminhar ou rejeitar. |

Uma requisição pausada é encaminhada sem alteração depois de 120 segundos, para a página não travar. O tempo pode ser mudado na configuração.

## Atalhos do teclado

Valem na tela de navegador e nos módulos de navegador do rack.

| Atalho | Ação |
|---|---|
| `Ctrl` + `L` | Editar o endereço |
| `F5` ou `Ctrl` + `R` | Recarregar |
| `Alt` + `←` / `→` | Voltar / avançar |
| `Alt` + `Home` | Página inicial |
| `Ctrl` + `T` / `Ctrl` + `W` | Nova aba / fechar aba |
| `Ctrl` + `Tab`, `Ctrl` + `1`…`8` | Trocar de aba |
| `Ctrl` + `+` / `-` / `0` | Zoom |
| `F11` | Tela cheia |
| `Esc` | Sair do teclado |

## Abas do modo criativo

NCAT Navegador · NCAT SSH · NCAT Terminal · NCAT Proxy · NCAT Conexão Remota · NCAT Cabos · NCAT Equipamentos de Rede · NCAT Estação de Trabalho · NCAT Rack e Módulos · NCAT Móveis

## Receitas

Tudo pode ser fabricado no modo sobrevivência. Os nomes aparecem no idioma do jogo.

<details>
<summary><strong>Ver todas as receitas</strong></summary>

**Telas**

| Item | Ingredientes |
|---|---|
| Tela NCAT Navegador | 4× lingote de ferro, 3× painel de vidro, 1× redstone, 1× corante vermelho |
| Tela NCAT SSH | 4× lingote de ferro, 3× painel de vidro, 1× redstone, 1× corante azul |
| Tela NCAT SFTP | 4× lingote de ferro, 3× painel de vidro, 1× redstone, 1× corante ciano |
| Tela NCAT Log | 4× lingote de ferro, 3× painel de vidro, 1× redstone, 1× corante laranja |
| Tela NCAT Ferramentas de Desenvolvedor | 4× lingote de ferro, 3× painel de vidro, 1× redstone, 1× corante roxo |
| Tela NCAT Workstation | 4× lingote de ferro, 3× painel de vidro, 1× Tela NCAT Navegador, 1× redstone |
| Tela de Terminal NCAT | 7× lingote de ferro, 1× painel de vidro, 1× redstone |
| Tela NCAT Conexão Remota | 4× lingote de ferro, 3× painel de vidro, 1× redstone, 1× corante verde |
| Tela de Proxy NCAT | 7× lingote de ferro, 1× painel de vidro, 1× quartzo |

**Teclados**

| Item | Ingredientes |
|---|---|
| Teclado NCAT Navegador | 3× lingote de ferro, 3× placa de pressão de pedra, 2× corante vermelho, 1× redstone |
| Teclado NCAT SSH | 3× lingote de ferro, 3× placa de pressão de pedra, 2× corante azul, 1× redstone |
| Teclado NCAT SFTP | 3× lingote de ferro, 3× placa de pressão de pedra, 2× corante ciano, 1× redstone |
| Teclado NCAT Ferramentas de Desenvolvedor | 3× lingote de ferro, 3× placa de pressão de pedra, 2× corante roxo, 1× redstone |
| Teclado NCAT Workstation | 4× lingote de ferro, 2× placa de pressão de pedra, 1× lingote de cobre, 1× Teclado NCAT Navegador, 1× redstone |
| Teclado de Terminal NCAT | 3× quartzo, 2× lingote de ferro, 1× lingote de cobre |
| Teclado de Proxy NCAT | 3× quartzo, 2× lingote de ferro, 1× fragmento de ametista |

**Rede e cabos**

| Item | Ingredientes |
|---|---|
| Cabo de dados NCAT | 2× lingote de cobre, 2× linha, 1× redstone |
| Porta de dados RJ45 | 2× pepita de ferro, 2× lingote de cobre, 1× redstone |
| Ferramenta de instalação de portas | 2× lingote de ferro, 1× redstone, 1× graveto |
| Ajustador de cabos | 4× lingote de ferro, 1× graveto |
| Cabo USB NCAT | 2× pepita de ferro, 2× linha, 1× lingote de cobre |
| Porta USB | 2× pepita de ferro, 1× lingote de cobre, 1× redstone |
| Instalador de portas USB | 2× lingote de ferro, 2× graveto, 1× Porta USB |
| Ajustador de cabo USB | 2× lingote de ferro, 2× graveto |
| Cabo serial NCAT | 2× lingote de cobre, 2× pepita de ferro, 1× redstone |
| Switch NCAT não gerenciado (8 portas) | 8× Porta de dados RJ45, 1× comparador de redstone |
| Switch NCAT gerenciável (8 portas) | 2× lingote de cobre, 1× redstone, 1× comparador de redstone, 1× Switch NCAT não gerenciado (8 portas) |
| Tablet de configuração NCAT | 4× lingote de ferro, 1× painel de vidro, 1× redstone, 1× laje de pedra lisa |

**Rack**

| Item | Ingredientes |
|---|---|
| Rack de Piso 12 U | 6× lingote de ferro, 2× painel de vidro, 1× barras de ferro |
| Extensão de Rack 18 U | 8× lingote de ferro, 1× Rack de Piso 12 U |
| Rack de Parede 6 U | 4× lingote de ferro, 1× painel de vidro, 1× barras de ferro |
| Navegador para rack | 7× lingote de ferro, 1× Tela NCAT Navegador, 1× redstone |
| SSH para rack | 7× lingote de ferro, 1× Tela NCAT SSH, 1× redstone |
| SFTP para rack | 7× lingote de ferro, 1× Tela NCAT SFTP, 1× redstone |
| Log para rack | 7× lingote de ferro, 1× Tela NCAT Log, 1× redstone |
| Ferramentas de desenvolvedor para rack | 7× lingote de ferro, 1× Tela NCAT Ferramentas de Desenvolvedor, 1× redstone |
| Terminal para rack | 3× lingote de ferro, 2× painel de vidro, 1× redstone |
| Desktop remoto para rack | 3× lingote de ferro, 2× painel de vidro, 1× pérola do Ender |
| Proxy para rack | 3× lingote de ferro, 2× painel de vidro, 1× quartzo |
| Switch para rack | 7× lingote de ferro, 1× Switch NCAT não gerenciado (8 portas), 1× redstone |
| Switch gerenciável para rack | 7× lingote de ferro, 1× Switch NCAT gerenciável (8 portas), 1× redstone |

**Ferramentas**

| Item | Ingredientes |
|---|---|
| Ferramenta de vínculo | 1× redstone, 1× lingote de ferro, 1× graveto |
| Configurador de tela | 2× lingote de ferro, 1× painel de vidro, 1× redstone, 1× graveto |
| Apontador laser | 1× painel de vidro, 1× redstone, 1× lingote de cobre |
| Inspetor laser de Log | 2× lingote de ferro, 1× fragmento de ametista, 1× redstone |
| Tablet de requisição NCAT | 7× lingote de ferro, 1× painel de vidro, 1× redstone |

**Móveis**

| Item | Ingredientes |
|---|---|
| Cadeira gamer NCAT Gato Azul | 3× lã preta, 2× lã azul, 2× lingote de ferro, 1× couro, 1× laje de pedra |
| Vaso sanitário NCAT | 6× bloco de quartzo, 1× lingote de ferro |
| Bebedouro NCAT | 5× lingote de ferro, 1× concreto branco, 1× redstone, 1× caldeirão, 1× lápis-lazúli |
| Copo descartável | 1× papel |
| Copo descartável com água | 1× Copo descartável, 1× balde de água |
| Relógio digital (hora do mundo) | 5× concreto preto, 2× redstone, 1× painel de vidro |
| Relógio digital (hora local) | 5× concreto branco, 2× redstone, 1× painel de vidro |
| Patinho de borracha | 4× corante amarelo, 1× bola de slime |
| Adaptador Wi-Fi ALTA | 3× lingote de ferro, 2× concreto preto, 2× bloco de quartzo, 1× redstone |

O **Galão de água NCAT** usa uma receita especial: 8 blocos de vidro em volta de um balde de água, preenchendo a grade 3×3. O galão sai cheio.

</details>

## Configuração

Os arquivos ficam na pasta `config` do Minecraft e são criados na primeira execução.

### `ncat_minecraft_client.toml` (cada jogador)

| Opção | Padrão | Descrição |
|---|---|---|
| `terminal_enabled` | `true` | Permite que a tela de terminal abra um shell no seu computador. |
| `terminal_directory` | vazio | Pasta inicial do terminal. Vazio usa a sua pasta pessoal. |
| `proxy_enabled` | `true` | Permite que o proxy entre no caminho das requisições. Desligado, ele só observa. |
| `proxy_hold_seconds` | `120` | Tempo máximo (5 a 600 s) que uma requisição pausada espera antes de seguir sem alteração. |
| `proxy_max_body_kib` | `1024` | Maior corpo (16 a 8192 KiB) que o proxy guarda em memória. |
| `load_distance` | `30` | Distância, em blocos, em que as telas começam a renderizar. |
| `unload_distance` | `32` | Distância em que as telas param de renderizar. |
| `input.keyboard_camera` | `true` | Aproxima a câmera do campo em foco enquanto você digita. |

### `ncat_minecraft_common.toml` (servidor e jogo local)

| Opção | Padrão | Descrição |
|---|---|---|
| `browser_options.home_page` | `mod://ncat_minecraft/main.html` | Página inicial das telas de navegador. |
| `browser_options.blacklist` | vazio | Domínios bloqueados em todas as telas. |
| `screen_options.max_width` / `max_height` | `16` / `16` | Tamanho máximo de uma tela, em blocos. |
| `screen_options.max_resolution_x` / `_y` | `1920` / `1080` | Resolução máxima de uma tela. |
| `mini_server.miniserv_port` | `25566` | Porta TCP do servidor de arquivos herdado do WebDisplays. Use `0` para desligar. |
| `mini_server.miniserv_quota` | `1024` | Cota por jogador desse servidor de arquivos, em KiB. |

O arquivo também traz opções herdadas do WebDisplays que não afetam os recursos do NCAT.

## Compilar a partir do código-fonte

### Pré-requisitos

- **JDK 17**, por exemplo o [Eclipse Temurin](https://adoptium.net/temurin/releases/?version=17).
- **Git**.
- **Internet no primeiro build**: o Gradle baixa o Forge, o Minecraft e os mapeamentos. Os builds seguintes podem ser offline.

### Compilar

```bash
git clone <url-deste-repositório>
cd ncat_minecraft
./gradlew build
```

No Windows, use `gradlew.bat build`.

O resultado fica em `build/libs/`:

| Arquivo | Uso |
|---|---|
| `ncat_minecraft-<versão>.jar` | **O mod completo.** É este que vai para a pasta `mods`. |
| `ncat_minecraft-<versão>-slim.jar` | Sem as bibliotecas embutidas (JSch e Netty). Não serve para jogar. |

Se o Gradle não encontrar o Java 17 (erro de *toolchain*), aponte a variável `JAVA_HOME` para um JDK 17:

```bash
export JAVA_HOME=/caminho/para/jdk-17
./gradlew build
```

```powershell
$env:JAVA_HOME = "C:\caminho\para\jdk-17"
.\gradlew.bat build
```

Depois do primeiro build, `./gradlew build --offline` compila sem acessar a rede.

### Rodar em desenvolvimento

```bash
./gradlew runClient
./gradlew runServer
```

As duas tarefas usam a pasta `run/`. Para configurar o IntelliJ IDEA ou o Eclipse, importe o projeto como Gradle e rode `./gradlew genIntellijRuns` ou `./gradlew genEclipseRuns`.

### Mudar a versão

Altere `mod_version` em `gradle.properties` **e** `version` em `src/main/resources/META-INF/mods.toml`.

### Regenerar modelos e texturas

Modelos, texturas, estados de bloco e ícones são gerados por scripts em `tools/`. É preciso **Python 3.10+** e **Pillow**. As prévias também usam **NumPy**.

```bash
pip install pillow numpy
python tools/gen_rack.py
python tools/create_rack_visuals.py
python tools/gen_switch.py
```

Rode os scripts a partir da raiz do projeto. `tools/strip_comments.py` remove comentários do código Java e Python, preservando os cabeçalhos de licença.

### Bibliotecas nativas do RDP

Os pacotes prontos ficam em `src/main/resources/assets/ncat_minecraft/native/rdp/` (`windows-x64.zip` e `linux-x64.zip`). Para reconstruí-los:

- Ponte JNI: `src/main/native/remote/` (CMake).
- Windows: `tools/package_windows_rdp.ps1`.
- Linux: `tools/package_linux_rdp.sh`.
- Bundle do noVNC: `node tools/build_novnc_bundle.mjs`.

## Estrutura do projeto

```text
ncat_minecraft/
├── build.gradle, settings.gradle, gradle.properties   build
├── src/main/java/…/ncatminecraft/
│   ├── block/, entity/, item/, registry/              blocos, entidades, itens e registros
│   ├── client/
│   │   ├── proxy/, terminal/, ssh/, sftp/, remote/     serviços de cada tela
│   │   ├── rack/, workstation/, log/, devtools/        rack, workstation e análise
│   │   ├── renderers/                                  renderização (cabos, rack, switches)
│   │   └── gui/                                        interfaces
│   ├── net/                                            pacotes de rede
│   └── utilities/                                      cabos, telas, recuperação
├── src/main/resources/
│   ├── assets/ncat_minecraft/html/                     páginas das telas
│   ├── assets/ncat_minecraft/lang/                     pt_br.json e en_us.json
│   └── data/ncat_minecraft/                            receitas e loot tables
├── src/main/native/remote/                             ponte JNI do RDP
├── tools/                                              geradores de modelos e empacotamento
└── docs/                                               imagens e roteiros de teste
```

## Segurança e uso responsável

- Use SSH, RDP, VNC, terminal e proxy **apenas em sistemas seus ou com autorização explícita**.
- O **terminal** abre um shell no computador do jogador, com o usuário dele, e nunca no servidor. Para desligá-lo, use `terminal_enabled = false`.
- O **proxy** só afeta os navegadores do próprio cliente. Para mantê-lo só observando, use `proxy_enabled = false`.
- Senhas de SSH e RDP ficam no cliente e não são gravadas no mundo.
- As telas são visíveis a qualquer jogador por perto. Log, ferramentas de desenvolvedor e proxy mostram cabeçalhos e corpos de requisições, que podem conter cookies e tokens.
- O servidor de arquivos herdado abre a porta TCP `25566` quando um mundo é carregado. Se não usar, desligue com `miniserv_port = 0`.
- Use `browser_options.blacklist` para bloquear domínios em todas as telas.

## Solução de problemas

| Sintoma | O que fazer |
|---|---|
| O jogo não abre e acusa falta do `mcef` | Instale o MCEF 2.1.1 para Forge 1.20.1 na pasta `mods`. |
| A tela fica preta na primeira vez | O MCEF ainda está baixando o Chromium. Espere e veja o log do jogo. |
| A tela não liga | Confira se o retângulo está completo, sem blocos faltando, e se tem pelo menos 2 blocos de largura ou altura. |
| SSH ou SFTP falham com `NoClassDefFoundError` | Você está usando o arquivo `-slim.jar`. Troque pelo jar completo. |
| RDP indisponível | O RDP só existe em Windows x64 e Linux x64. O VNC funciona sem biblioteca nativa. |
| O terminal não lista o WSL | Rode `wsl --list` no Windows para conferir se há distribuições instaladas. |
| A página do proxy não carrega no modo ativo | Volte para **Observar** e confira o histórico. Um redirecionamento em laço é interrompido após 5 saltos. |
| Build falha com erro de *toolchain* | Aponte `JAVA_HOME` para um JDK 17. |

## Contribuindo

- Abra uma *issue* descrevendo o problema ou a ideia antes de um *pull request* grande.
- Todo texto visível ao jogador precisa existir em `pt_br.json` **e** `en_us.json`. As páginas HTML trazem os dois idiomas no próprio arquivo.
- O código segue sem comentários. Rode `python tools/strip_comments.py` antes de enviar.
- Modelos e texturas são gerados pelos scripts de `tools/`: altere o script, não o JSON gerado.
- Roteiros de teste manual estão em [`docs/testes/`](docs/testes/).

## Créditos e licenças

- Baseado no **[WebDisplays](https://github.com/CinemaMod/webdisplays)**, criado por montoyo e mantido pelo CinemaMod Group, sob licença **MIT**. Veja [LICENSE](LICENSE).
- **MCEF** (CinemaMod) é uma dependência separada e não é redistribuída aqui.
- Bibliotecas incluídas: **JSch 2.28.7** (BSD revisada e ISC), **Netty 4.1.82** (Apache 2.0), **xterm.js 5.5.0** e **addon-fit 0.10.0** (MIT), **noVNC 1.7.0** (MPL 2.0) e **FreeRDP 3.32.1** (Apache 2.0).
- Detalhes, versões e avisos completos em [NOTICE.md](NOTICE.md).

Minecraft é marca da Mojang Studios. Este projeto não é oficial e não tem ligação com a Mojang ou a Microsoft.
