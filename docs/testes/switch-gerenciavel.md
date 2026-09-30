# Teste do switch gerenciável NCAT

Use Minecraft 1.20.1, Forge 47.1.65 e o JAR entregue para este teste. Faça os testes em um mundo de laboratório e retire versões antigas do `ncat_minecraft` da pasta `mods`.

## Instalação e conexão

1. Na aba **Equipamentos de Rede**, coloque o **Switch NCAT gerenciável (8 portas)** e o **Tablet de configuração NCAT** no chão, a até 16 blocos um do outro. A pessoa que coloca o switch fica responsável por sua configuração; operadores do servidor também podem configurá-lo.
2. Pegue o **Cabo serial NCAT**. Clique com o botão direito na entrada serial da lateral direita do tablet e depois na entrada serial da lateral direita do switch. As duas pontas podem ser conectadas em qualquer ordem. Mire perto da entrada física, não no painel frontal. O cabo deve aparecer entre os aparelhos.
3. Clique com a mão vazia no tablet. A interface deve abrir com **Visão geral**, **Portas**, **Políticas** e **Diagnóstico**. Feche e abra novamente: a seção e a porta selecionada devem continuar salvas. A tela do tablet colocado no mundo também deve refletir a seção ativa e ser visível para outro jogador.
4. Conecte o **cabo de dados** às portas frontais `eth0` a `eth7`. O cabo serial serve apenas para configurar o switch; o tráfego dos blocos NCAT passa pelos cabos de dados.

## Encaminhamento e regras

5. Conecte uma workstation, um navegador e um SSH ao switch. Ligue o switch no botão frontal ou em **Visão geral**. A workstation deve descobrir as duas telas. Confira em **Portas** os pares conectados, os rótulos e a VLAN de cada porta.
6. Em **Políticas**, bloqueie o par de portas usado pela workstation e pelo navegador. O navegador deve desaparecer da workstation, enquanto o SSH permanece disponível. Libere o par: o navegador deve reaparecer.
7. Em **Portas**, desative uma porta conectada e reative. Teste também VLANs diferentes em duas portas de acesso: a comunicação entre elas deve parar. Para transportar várias VLANs entre switches gerenciáveis, ative **Trunk** nos dois lados do enlace. Volte à VLAN 1 ao terminar o teste.
8. Encadeie dois switches gerenciáveis e um não gerenciado. Ligue a workstation em uma ponta e as telas na outra. Confira a descoberta de telas através das portas de uplink e teste desligar um switch intermediário. Em **Diagnóstico**, observe os enlaces e contadores de eventos enquanto usa as telas.

## Persistência e recuperação

9. Salve e reabra o mundo. Confira rótulos, VLANs, portas desativadas, regras, ligação serial, seção escolhida no tablet e estado de energia.
10. Agache e clique com o cabo serial em uma das entradas para desconectar e recuperar o item. Refaça a ligação. Quebre o tablet ou o switch conectado: o cabo deve ser devolvido uma vez, e o aparelho restante deve aceitar uma nova ligação quando estiver carregado.
11. Repita o teste com outro jogador. A tela física deve ser visível para ambos; a edição de configuração só deve funcionar para o dono ou um operador. Teste também quando o tablet e o switch estiverem em chunks carregados diferentes.

## Tablet portátil e portas USB

12. Com a mão vazia, agache e clique com o botão direito no tablet encaixado. O tablet deve passar para o inventário e a base com o cabo serial deve continuar no chão. Segure o tablet, clique com o botão direito e confira a mesma configuração a até 12 blocos da base. Outro jogador deve ver o tablet na mão do personagem. Se o tablet estiver ligado ao switch, um jogador sem permissão não deve conseguir retirá-lo.
13. Afaste-se da base e confirme que a configuração deixa de responder. Em outra dimensão, o tablet vinculado não deve abrir a configuração nem virar um novo bloco. Volte e clique com o tablet na própria base para encaixá-lo novamente. Um tablet recém-criado ou pertencente a outra base não deve ocupar essa base.
14. Coloque teclados de navegador, SSH, SFTP, DevTools e workstation. A entrada USB deve aparecer na borda traseira de cada teclado, fora das teclas, inclusive depois de girar o bloco. Ligue um cabo USB e confira a posição do conector e da luz indicadora.

As políticas controlam somente os vínculos virtuais dos blocos NCAT. Elas não são regras de firewall do computador, do servidor Minecraft ou da rede externa. A descoberta e o encaminhamento exigem que os chunks envolvidos estejam carregados.
