# 🎹 MyChord

**Um instrumento musical de bolso para Android.** Toque acordes com um dedo, acrescente bateria, grave loops e até conecte um teclado MIDI. O app é nativo (Kotlin), sem anúncios, sem internet obrigatória e sem dependências pesadas.

> Antigo **HiChord**. O nome interno do pacote continua `com.hichord.app` de propósito, para o botão *Atualizar* instalar por cima das versões antigas.

---

## ✨ O que ele faz

- **7 pads de acordes** (graus I a VII) de qualquer tom, maior ou menor. Arraste o dedo entre os pads para trocar de acorde sem soltar.
- **7 modos de toque:** Play, Strum (palhetada), Arpejo, Drone, Lead, Sequencer e **Ouvido** (treino de percepção musical).
- **Efeitos:** Reverb, Delay ping-pong, Chorus e Flanger.
- **Bateria de 16 passos** com 8 pistas, 7 kits (808, Rock, Lo-Fi, Techno, Jazz, Eletro, Acústico) e 7 ritmos prontos.
- **Looper de 6 faixas** que grava o som já com os efeitos.
- **MIDI por USB-C** (entrada, saída e clock) para teclados, interfaces e DAWs.
- **Controle Bluetooth/gamepad** com modo de navegação por direcional.
- **Atualização pelo próprio app** (botão *Atualizar*).

Manual completo de uso: **[docs/GUIA-DE-USO.md](docs/GUIA-DE-USO.md)**

---

## 📲 Só quero usar (instalar no celular)

1. Abra a aba **[Releases](../../releases)** deste repositório (barra do lado direito da página, ou o link acima).
2. Na versão mais recente (a de número maior), toque em **`HiChord.apk`** para baixar.
3. Abra o arquivo baixado. O Android vai pedir para **permitir a instalação de apps desta fonte** (navegador ou gerenciador de arquivos): permita.
4. Se aparecer um aviso do **Play Protect**, toque em *Instalar mesmo assim*. O aviso é normal para apps que não vêm da Play Store.
5. Pronto. Depois disso, o botão **Atualizar** dentro do app avisa (fica colorido com ●) e instala as versões novas.

Requisitos: Android 7.0 ou mais novo.

---

## 🛠️ Quero mexer no código e criar minha versão

Você pode **copiar o projeto (fork), alterar e usar à vontade**, desde que mantenha o aviso de autoria (veja [Licença](#-licença-e-direitos-autorais)). Existem dois caminhos. O primeiro **não exige instalar nada no computador**.

### Caminho A: tudo pelo navegador (GitHub Actions compila o APK)

1. Crie uma conta gratuita em [github.com](https://github.com) e entre nela.
2. Nesta página, clique em **Fork** (canto superior direito) e depois em **Create fork**. Agora existe uma cópia do projeto na sua conta.
3. No seu fork, abra a aba **Actions** e clique no botão verde **"I understand my workflows, go ahead and enable them"**.
4. Edite um arquivo (por exemplo, `app/src/main/java/com/hichord/app/Synth.kt`): abra-o, clique no ícone de lápis ✏️, altere e clique em **Commit changes**.
5. Ao salvar, a aba **Actions** começa a compilar sozinha (leva de 5 a 10 minutos). Quando ficar com ✅, clique na execução e baixe o arquivo **`HiChord-apk`** (um zip com o `app-debug.apk`). Instale no celular como no passo anterior.

> Esse APK sai com uma chave temporária. Funciona normalmente, mas **não consegue atualizar por cima** de uma versão com outra chave (nesse caso, desinstale antes). Para ter atualização automática pelo app, faça o passo "Assinatura fixa" abaixo.

#### Assinatura fixa + botão Atualizar na sua versão (opcional)

Isso faz cada compilação virar uma *Release* e o botão **Atualizar** do app funcionar com **o seu** repositório (que precisa ser **público**).

1. Instale o [JDK 17](https://adoptium.net) (só uma vez) e crie a sua chave. Troque `MINHA_SENHA` por uma senha sua:

   ```bash
   keytool -genkeypair -v -storetype PKCS12 -keystore hichord.p12 -alias hichord \
     -keyalg RSA -keysize 2048 -validity 10000 -storepass MINHA_SENHA
   ```

   (O nome do alias precisa ser `hichord`; o workflow espera esse nome.)
2. Converta o arquivo para texto (base64):
   - Linux: `base64 -w0 hichord.p12`
   - macOS: `base64 -i hichord.p12`
   - Windows (PowerShell): `[Convert]::ToBase64String([IO.File]::ReadAllBytes("hichord.p12"))`
3. No seu fork vá em **Settings → Secrets and variables → Actions → New repository secret** e crie dois segredos:
   - `KEYSTORE_BASE64` = o texto gerado no passo 2
   - `KEYSTORE_PASSWORD` = a senha que você escolheu
4. Faça qualquer alteração (ou vá em **Actions → Build APK → Run workflow**). Ao terminar, a aba **Releases** terá o `HiChord.apk` assinado.

⚠️ **Guarde o arquivo `hichord.p12` e a senha num lugar seguro e nunca os envie para o GitHub.** Sem eles não dá para atualizar o app por cima. (O `.gitignore` do projeto já bloqueia esses arquivos.)

### Caminho B: no computador com Android Studio

1. Instale o [Android Studio](https://developer.android.com/studio) e o [Git](https://git-scm.com).
2. Baixe o projeto: `git clone https://github.com/Xaico04/mychord.git` (ou o endereço do seu fork), ou use o botão verde **Code → Download ZIP** e extraia.
3. No Android Studio: **File → Open** e escolha a pasta do projeto. Aguarde o *Gradle sync* terminar.
4. Conecte o celular com **depuração USB** ligada e clique em ▶ **Run**. Ou gere o APK em **Build → Build APK(s)**.

Pela linha de comando (precisa do Gradle 8.7 e do Android SDK): `gradle assembleDebug`.

---

## 🔊 Ajustando o som

Todo o motor de áudio fica em **`app/src/main/java/com/hichord/app/Synth.kt`**. Os pontos que mais interessam:

| Quero mudar… | Onde |
|---|---|
| Latência × estabilidade (se ainda estalar em celular fraco) | `start()`: o valor `768` é o tamanho inicial do buffer em *frames* (48 000 = 1 s). Suba para `1024` ou `1536`. O limite de crescimento automático é `2048`. |
| Ataque e liberação de cada modo | `HiChordView.kt`, função `setMode` (`atkT` e `relT`, em segundos) |
| Brilho/escuridão geral do som | `Engine.render`, linha com `lp += 0.25f * ...` (menor = mais escuro) |
| Onde o limitador começa a curvar | função `soft()` (o `0.6f`) |
| Sons dos kits de bateria | tabela `KIT_TXT` (frequência, decaimento, ruído, filtro) |
| Intensidade de Reverb, Delay, Chorus, Flanger | `Fx.process` e as constantes `RV_*` |
| Volume da bateria × instrumentos | `LG` (ganho por pista) e `drumVol` |

Dica: faça **uma alteração por vez** e teste no celular. Se algo piorar, volte a alteração pelo histórico do GitHub (botão *History* do arquivo).

O que foi corrigido nos estalos está descrito em [CHANGELOG.md](CHANGELOG.md).

---

## 📁 Estrutura do projeto

```
app/src/main/java/com/hichord/app/
├── MainActivity.kt   # tela cheia, rotação, atualização e controle (gamepad)
├── HiChordView.kt    # toda a interface (desenhada em Canvas), toques e menus
├── Synth.kt          # motor de áudio: vozes, bateria, looper, efeitos
├── MidiIO.kt         # MIDI por USB
└── Updater.kt        # botão "Atualizar" (consulta as Releases do GitHub)
app/src/main/res/     # ícone, mascote e cores
.github/workflows/    # build.yml: compila o APK e publica a Release
docs/                 # manual de uso
```

---

## 🤝 Quer contribuir de volta?

Achou um erro ou fez algo legal? Abra uma **[Issue](../../issues)** descrevendo, ou envie um **Pull Request**: faça um fork, altere, e no seu fork clique em **Contribute → Open pull request**. Só o autor do projeto original aprova e junta as mudanças aqui; ninguém altera este repositório sem permissão.

---

## 📜 Licença e direitos autorais

Código sob **licença MIT** (arquivo [LICENSE](LICENSE)). Em resumo, **qualquer pessoa pode** usar, copiar, modificar e criar novas versões **desde que mantenha o aviso "Copyright (c) 2026 Xaico04" e o texto da licença** em todas as cópias e derivados. Os direitos de autoria do projeto original continuam sendo do autor. O software é fornecido "como está", sem garantia.

## 💜 Créditos

- Criado por **Xaico04**.
- O visual "Synthos" é uma homenagem de fã ao episódio *"Gary's Synthesizer"* de *Apenas um Show*. Este projeto não tem relação com os criadores da série, e personagens, nomes e marcas citados pertencem aos seus respectivos donos. A licença MIT vale para o código e para a arte original do projeto, não para propriedade intelectual de terceiros.
