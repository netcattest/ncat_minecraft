# Teste da rede NCAT e da Workstation

Use Minecraft 1.20.1 com Forge 47.1.65 e a versão atual do NCAT Minecraft. Faça os testes em um mundo de laboratório.

1. Na aba **Equipamentos de Rede**, coloque o switch e clique no botão pequeno da frente para ligá-lo. A luz de estado deve mudar de vermelho para verde quando houver cabos conectados.
2. Monte e ative uma tela da aba **Workstation**. Ligue o teclado à porta USB da tela com o cabo USB.
3. Com o **cabo de dados**, clique em uma porta RJ45 da workstation e depois em uma das oito portas frontais do switch.
4. Ligue um navegador e um SSH a outras portas do mesmo switch. A workstation deve listar os dois no rodapé. Abra cada um e teste mouse, teclado e rolagem.
5. Ligue dois navegadores ao switch. Cada um deve aparecer como uma opção distinta na workstation. Se as portas acabarem, ligue outro switch ao primeiro; a lista deve continuar crescendo.
6. Ligue um log e um DevTools à rede que já contém navegadores. Cada tela dependente deve escolher um navegador compatível. Ligue um segundo navegador e confirme que a fonte atual não muda sozinha. Desconecte a fonte escolhida e confira se outra fonte disponível assume.
7. Repita o teste de seleção com um SFTP e dois SSH. O SFTP deve usar somente um SSH por vez.
8. Observe os LEDs nas portas durante a interação e o tráfego. Desligue o switch e confira se as telas deixam de aparecer na workstation. Ligue-o novamente e confira se retornam.
9. Quebre e recoloque uma tela conectada. Verifique se os cabos e a seleção de fonte se recuperam. Teste também com uma tela expandida por mais blocos.

Os cabos têm alcance físico de até 48 blocos cada, e os dispositivos precisam estar em chunks carregados. O número de dispositivos descobertos pela workstation não tem limite fixo: amplie a rede com mais switches.
