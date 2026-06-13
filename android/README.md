# Leitor de Legendas Disney+ (Android)

App Android nativa que **lê em voz alta as legendas do Disney+** no telemóvel.
É a versão móvel da extensão de browser *Leitor de Legendas para NVDA*: em vez
de ler o DOM da página, usa um **serviço de acessibilidade** para detetar o
texto das legendas apresentado pela app Disney+ e anuncia-o através do
sintetizador de voz (Text-to-Speech) do dispositivo.

Pensada para **acessibilidade** — apoio a quem tem visão reduzida ou prefere
ouvir as legendas.

## Como funciona

1. O serviço de acessibilidade está restringido, no manifesto, à app Disney+
   (`com.disney.disneyplus`), pelo que **só recebe eventos dessa app**.
2. A cada alteração de conteúdo, percorre a árvore de acessibilidade da janela
   ativa e recolhe os nós de texto na **zona inferior do ecrã** (onde aparecem
   as legendas), filtrando controlos da interface (botões, etc.).
3. Junta as linhas, ignora repetições consecutivas e entrega o texto ao
   Text-to-Speech.

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

- Se o conteúdo for reproduzido com proteção de ecrã (output protegido) ou as
  legendas forem desenhadas **diretamente sobre o vídeo** sem serem expostas na
  árvore de acessibilidade, não existe texto para ler — limitação inerente a
  esta abordagem em Android.
- A deteção assenta na posição do texto no ecrã; o controlo *Zona das legendas*
  permite afinar a app a diferentes layouts.
