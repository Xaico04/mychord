# Histórico de mudanças

## Correção: botão de ajuda

- A ajuda (e a abertura, ao tocar no nome) fechava no mesmo instante em que abria: o toque que abria soltava em cima do "×", que fica na mesma posição do "?". Agora o fim do toque que abriu é ignorado.

## Novo visual "Lab" (instrumento de hardware, mais sério)

- Placa lilás-acinzentada, **visor LCD preto com letras e osciloscópio em verde de fósforo**, aro claro em volta do visor.
- **Joystick metálico** com botão branco.
- Teclas claras e planas, com texto escuro e um pequeno traço de cor do grau; a tecla ativa fica verde-menta.
- Botões de função coloridos por fileira (ciano, verde-limão, amarelo, lilás): cor cheia quando ligados, abafada quando desligados.
- Tela de abertura minimalista (ondas verdes e o nome), sem mascote, planeta e cabos.
- Novo ícone do app. O desenho do mascote e a opção `mascote.png` foram removidos.
- Todas as cores ficam num bloco só em `HiChordView.kt` (`cores: paleta "Lab"`).

## Correção de áudio (estalos)

O som ficou com estalos e menos limpo depois que entraram bateria, looper e efeitos. Causas encontradas e corrigidas em `Synth.kt`:

- **Buffer de áudio minúsculo (~5 ms).** Qualquer atraso do celular virava um "estalo" (underrun). Agora o buffer começa em ~16 ms e **cresce sozinho** (até ~43 ms) se o Android avisar que faltou áudio. A thread de áudio também passou a usar a prioridade própria do Android (`THREAD_PRIORITY_URGENT_AUDIO`).
- **Distorção do limitador.** O `tanh` na saída distorcia até em volume baixo; agora o sinal fica linear (limpo) até 60% e só curva acima disso.
- **Chiado nas ondas Serra e Quadrada** (aliasing): osciladores agora usam PolyBLEP.
- **Troca de onda** com o app tocando dava um "tec": agora há um fade de 4 ms.
- **Bateria:** bumbo/tom começavam no meio da senoide (estalo no ataque), o hat fechado cortava o aberto de uma vez, e retoques davam degrau. Agora há rampa de 1,5 ms no ataque, fase zerada ao iniciar e abafamento do hat em 6 ms.
- **Looper:** as pontas do loop agora têm fade de 3 ms (antes, o corte gerava um "tic" a cada volta).
- Proteção contra números denormais nos atrasos de efeito (evita picos de CPU).
- Tela: o osciloscópio reaproveita o `Path` em vez de criar um novo a cada quadro (menos pausas do coletor de lixo).

## Publicação no GitHub

- O botão **Atualizar** agora usa o repositório de quem compilou o app (`BuildConfig.REPO`), então cada fork atualiza pelas próprias releases.
- O workflow também compila Pull Requests (sem publicar release).
- Licença MIT, `.gitignore` (protege as chaves de assinatura) e documentação.
