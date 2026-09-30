# Teste do rack NCAT

Use **Minecraft 1.20.1**, **Forge 47.1.65**, **MCEF 2.1.1** e o JAR atualizado do NCAT Minecraft. Os módulos de rack não têm tela própria: o conteúdo aparece na **workstation** conectada pela rede de dados.

## Montagem e operação

1. Coloque uma **estrutura de rack de 12 U** no chão. Empilhe outra estrutura de 12 U para chegar a **24 U**. Sobre as duas, coloque a **extensão de 18 U** para chegar a **42 U**. Os três blocos precisam apontar para o mesmo lado; o encaixe da extensão ajusta a orientação automaticamente.
2. Com um **módulo para rack** na mão, clique na frente da estrutura. A altura do clique indica a unidade preferida; o rack procura outra unidade livre caso ela esteja ocupada. Os módulos de navegador, SSH, log, DevTools e SFTP ocupam **1 U**. Cada switch ocupa **2 U**.
3. Clique com a mão vazia para abrir o painel. Também é possível abrir o painel com **Shift + clique** segurando um módulo ou um cabo de dados. Selecione um módulo para ligar, desligar, retirar ou abrir suas portas.
4. Use **Shift + clique com a mão vazia** para abrir ou fechar a porta de vidro. Com a porta aberta, clique no pequeno botão à direita da frente de um módulo para alternar a energia. O painel também oferece esse controle.
5. No painel, selecione o módulo e abra **Portas**. Segure um **cabo de dados** e clique em `eth0`, `eth1` etc. Abra a porta de outro módulo ou clique na entrada de dados de um equipamento externo para conectar a outra ponta. O painel aproxima visualmente a frente do módulo. Os cabos saem pela lateral do rack e podem ser ajustados com a ferramenta de ajuste de cabo.
6. Para ampliar um módulo, tenha um item **porta de dados** no inventário e clique em **+ porta**. Equipamentos comuns aceitam até **4** portas e switches até **16**. O switch começa com **8** portas.

## Rede e telas

1. Ligue um módulo de navegador e um módulo de switch. Conecte o navegador a uma porta do switch; conecte outra porta à workstation. O **Navegador para rack** deve aparecer no rodapé da workstation. Selecione-o e teste navegação, mouse, rolagem e teclado conectado à workstation.
2. Acrescente módulos de SSH, log, DevTools e SFTP. Ligue o log e o DevTools ao navegador e o SFTP ao SSH, diretamente ou pela rede de switches. Após estabelecer uma sessão SSH, confirme que o SFTP encontra essa sessão.
3. Conecte um segundo rack ao primeiro por switches. Ligue apenas um cabo entre o rack próximo e a workstation; os módulos remotos ligados e alcançáveis devem aparecer no rodapé.
4. No módulo de **switch gerenciável**, abra **Configurar switch**. Desative uma porta, mude a VLAN ou bloqueie a comunicação entre duas portas. Confirme que a lista da workstation e os módulos dependentes respeitam a política. Restaure as configurações e confirme o retorno.
5. Observe o LED de cada módulo: **vermelho** quando desligado, **âmbar** quando ligado sem conexão, **verde** com conexão e indicação de atividade quando há tráfego. Os LEDs das portas devem responder à conexão e ao uso.
6. Salve o mundo, saia e entre novamente. Confirme que tamanho, módulos, energia, portas, cabos e políticas continuam corretos. Quebre uma seção de um rack de teste: os módulos devem cair como itens e os cabos ligados a eles devem ser removidos.

Cada cabo de dados alcança até **48 blocos**. Os equipamentos precisam estar em chunks carregados. No teste de SSH, use somente um servidor seu ou de laboratório autorizado.
