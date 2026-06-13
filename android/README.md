# Leitor de Legendas Disney+ (Android)

App Android nativa que **lê em voz alta as legendas do Disney+** no telemóvel.
É a versão móvel da extensão de browser *Leitor de Legendas para NVDA*: em vez
de ler o DOM da página, usa um **serviço de acessibilidade** para detetar o
texto das legendas apresentado pela app Disney+ e anuncia-o através do
sintetizador de voz (Text-to-Speech) do dispositivo.

Pensada para **acessibilidade** — apoio a quem tem visão reduzida ou prefere
ouvir as legendas.

## Como funciona

A view das legendas do Disney+ **não é uma "live region"**: alterar o texto não
gera um evento de acessibilidade, e por isso o TalkBack só as lê quando o
utilizador move o foco com o dedo. Para ler em automático, esta app **sonda
(polling)** a árvore de acessibilidade em vez de esperar por eventos — o
equivalente Android ao polling do DOM que a extensão de browser fazia.

1. Enquanto o Disney+ está em primeiro plano, a cada ~300 ms o serviço percorre
   **todas as janelas** (`getWindows()`) pertencentes ao Disney+ — as legendas
   estão normalmente numa **janela separada** (overlay do player), que a janela
   ativa sozinha não alcança.
2. Recolhe os nós de texto na **zona inferior do ecrã** (onde aparecem as
   legendas), filtrando controlos da interface (botões, relógios, etc.).
3. Junta as linhas, ignora repetições e entrega o texto ao Text-to-Speech.

O polling pára automaticamente quando o Disney+ deixa de estar em primeiro plano
(poupa bateria) e recomeça quando volta.

### Privacidade

- **Sem permissão de Internet.** Todo o processamento é local.
- As legendas **não são gravadas nem enviadas** para qualquer servidor.
- O serviço só observa a app Disney+ (ver `accessibility_service_config.xml`).

## Utilização

1. Instale a app e abra-a.
2. Toque em **Abrir definições de acessibilidade** e ative o **Leitor de
   Legendas**.
3. No Disney+, ligue as legendas (CC) do conteúdo.
4. Reproduza o vídeo — as legendas são lidas em voz alta.

Na app é possível ajustar: idioma da voz, velocidade, tom, se a nova legenda
interrompe a anterior, e a altura da "zona das legendas" (caso apareçam mais
acima no vídeo).

## Compilar

Requer **Android SDK** (via Android Studio ou linha de comandos) e JDK 17.

```bash
cd android
# Defina o caminho do SDK (ou crie local.properties com sdk.dir=...)
echo "sdk.dir=$ANDROID_HOME" > local.properties

./gradlew assembleDebug
# APK gerado em app/build/outputs/apk/debug/app-debug.apk
```

Ou abra a pasta `android/` no **Android Studio** e carregue em *Run*.

### Stack

- Kotlin · `minSdk 26` (Android 8.0) · `targetSdk 34`
- AndroidX + Material 3 · ViewBinding
- Android Gradle Plugin 8.6.1 · Gradle 8.9

## Estrutura

```
android/
├── app/src/main/
│   ├── java/com/legendas/leitor/
│   │   ├── MainActivity.kt                  # Ecrã de definições e estado
│   │   ├── SubtitleAccessibilityService.kt  # Deteção das legendas
│   │   ├── TtsManager.kt                    # Leitura em voz alta (TTS)
│   │   └── Prefs.kt                         # Preferências do utilizador
│   ├── res/                                 # Layouts, strings (PT), tema, ícone
│   └── AndroidManifest.xml
└── build.gradle.kts
```

## Limitações conhecidas

- A app só consegue ler legendas que existam na **árvore de acessibilidade**
  (as mesmas que o TalkBack consegue ler ao focar). Se o conteúdo expuser as
  legendas como imagem/sobre o vídeo sem nó de texto, não há o que ler — é uma
  limitação inerente a esta abordagem em Android.
- A deteção assenta na posição do texto no ecrã; o controlo *Zona das legendas*
  permite afinar a app a diferentes layouts.
- O polling tem um custo mínimo de bateria enquanto o Disney+ está aberto.
