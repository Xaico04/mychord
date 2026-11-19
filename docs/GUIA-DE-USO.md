# Guia de uso do MyChord

## Primeiros passos

- A tela abre sempre **deitada**. Ela só gira para vertical se a rotação automática do celular estiver **ligada**.
- **Volume:** o app sempre toca em 100%. Use os botões de volume do celular.
- **Acordes:** toque nos pads (graus I a VII). Arraste o dedo entre os pads, sem soltar, para trocar de acorde.
- **Capa animada:** fecha sozinha em ~4 s (ou toque na tela). Toque no nome "MyChord" (canto superior esquerdo) para rever.
- **Botão "?"** (canto superior direito): ajuda por seção (Início, Acordes, Modos, Efeitos, Bateria, Loop, MIDI, Controle).
- **Fone com DAC USB:** conecte antes de abrir o app; o Android usa o DAC sozinho.
- Quer uma imagem sua na capa? Salve como `app/src/main/assets/mascote.png` (quadrada, de preferência com fundo transparente) e gere o app de novo.

## Modos de toque (botão MODO, na última célula da grade de pads)

| Modo | Como funciona |
|---|---|
| **Play** | Acorde sustentado enquanto o pad está apertado. |
| **Strum** | As notas entram em sequência como corda e decaem sozinhas. Cada toque alterna entre palhetada para baixo e para cima. |
| **Arpejo** | Percorre o acorde em duas oitavas enquanto o pad está apertado. |
| **Drone** | Toca como o Play e o grave (fundamental + quinta) continua soando depois de soltar, deslizando para o próximo acorde. Troque de modo para parar. |
| **Lead** | Cada pad vira um grau da escala (nota única, com glissando). Analógico: cima = oitava acima, baixo = oitava abaixo, direita = vibrato, esquerda = 2 oitavas acima. |
| **Sequencer** | Toque um acorde e a pista "Melodia" da bateria toca as notas dele no andamento. Liga o transporte sozinho. |
| **Ouvido** | Treino de ouvido: o app toca a tônica e uma nota; responda tocando o pad do grau (I a VII). Toque no visor para ouvir de novo. Dificuldade na página MIDI: Fácil (I, III, V), Normal (7 graus), Difícil (a referência é um acorde, sem a tônica solta). |

## Efeitos e ajustes (botão FX)

- **Reverb, Delay (eco ping-pong), Chorus e Flanger:** cada toque muda a intensidade (off → 30% → 60% → 100% → off).
- **Arp:** direção (Sobe, Desce, Vai-vem, Aleatório) e andamento (70 a 180 bpm).
- **Eco:** tempo do delay (150, 250, 350 ou 450 ms).
- Tudo fica salvo e volta igual na próxima vez que abrir o app.

O botão do topo (ao lado de Atualizar) troca a página dos botões: **Menu → FX → Loop → Bateria → MIDI**.

## Bateria (página "Bateria")

- O visor vira a grade de **16 passos**. No topo ficam as 8 pistas: toque no nome para escolher e toque nos passos para ligar/desligar.
- Pistas: Kick, Caixa, Hat fechado, Hat aberto, Palma, Tom, Perc e Melodia (a Melodia aceita valores 1 a 8: 1-4 = notas do acorde, 5-8 = as mesmas uma oitava acima; toque várias vezes para trocar).
- **7 kits:** 808, Rock, Lo-Fi, Techno, Jazz, Eletro, Acústico. **7 ritmos prontos:** Vazio, Rock, Hip-hop, Disco, Techno, Reggae, Funk.
- Botões: Kit, Pista, Tocar/Parar, BPM (70–180), Ritmo, Limpar pista, Loop (1, 2 ou 4 compassos), volume da bateria.
- A bateria sai seca (sem reverb/delay) e fica salva ao fechar o app.

## Looper (página "Loop")

- 6 faixas. Toque em **T1..T6**: grava no próximo início de compasso, durante 1 loop inteiro, e passa a tocar sozinha. Toque de novo para mudo/ativar. Faixa em gravação ou aguardando: o toque cancela.
- Grava o som dos modos de toque já com os efeitos (a bateria não entra na gravação: ela toca pelo sequenciador).
- Andamento e tamanho do loop ficam travados enquanto houver faixa gravada: use **Limpar tudo** para liberar.
- As gravações **não** são salvas ao fechar o app.

## MIDI (página "MIDI", USB-C com cabo OTG)

- **MIDI:** toque para passar pelos dispositivos encontrados (teclado, interface, ou o próprio celular em modo MIDI USB ligado a um computador). Depois do último, desliga.
- **Saída:** envia as notas tocadas (canal escolhido) e a bateria (canal 10, notas General MIDI).
- **Entrada:** um teclado externo toca o som do app.
- **Clock:** envia o relógio MIDI + Start/Stop do transporte (para DAWs e baterias externas).
- **Notas OFF:** corta qualquer nota presa no aparelho externo.

## Controle Bluetooth / gamepad

- **Tocar:** LB, RB, LT, RT, X, A, B = acordes I a VII. Analógico esquerdo = variações. **Y** = próximo modo. **Start** = liga/desliga o ritmo.
- **Direcional:** esquerda/direita = Tom −/+, cima = Maior/Menor, baixo = Onda.
- **R3** = próxima página, **L3** = página anterior.
- **Select** = modo **EDITAR**: o direcional move uma moldura branca por todos os botões, passos e pistas da Bateria e barra de cima; **A** confirma; **B** ou **Select** sai (no EDITAR, A e B não tocam acorde).
