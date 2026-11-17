# ClipPilot Android

Editor de vídeos Android — **versão 0.1 (base funcional e visual)**.

## O que está implementado
- Aplicativo nativo Kotlin + Jetpack Compose, identidade visual escura/ciano.
- Início, Projeto, Projetos e Prévia.
- Seleção de até 3 vídeos pela interface oficial do Android (Storage Access Framework).
- Permissões de leitura persistentes e projeto preservado ao reiniciar o app.
- Miniaturas e duração de vídeos importados.
- Prévia real de cada vídeo com Media3 ExoPlayer.
- Seleção dos estilos Viral, Profissional e Cinematográfico (opções de UI para próximas etapas).
- GitHub Actions compila um APK de depuração instalável.

## Ainda NÃO implementado
A versão 0.1 **não** faz montagem automática, exportação MP4, cortes, efeitos, transições, correção de cor nem legendas automáticas. Os estilos são apenas escolhas armazenadas na tela. Não há IA paga ou conexão com servidores. Estas funções serão implementadas e validadas nas próximas etapas.

## APK
Abra **Actions → Android APK → execução mais recente → Artifacts → ClipPilot-debug-apk**, baixe o ZIP e instale o arquivo APK extraído em um dispositivo Android.

O APK de debug é assinado automaticamente para testes; para atualizações estáveis e distribuição pública, configurar uma chave de assinatura de release protegida.

## Desenvolvimento
- JDK 17
- Android SDK 35
- Gradle 8.9
- Android Gradle Plugin 8.7.3
- Kotlin 2.0.21 + Compose
- Media3 1.5.1

Compilar: `gradle :app:assembleDebug`

## Próximas entregas
1. Montagem e exportação dos três vídeos (Media3 Transformer).
2. Cortes automáticos e transições suportadas.
3. Ajustes de brilho, enquadramento/zoom e áudio.
4. Whisper local para transcrição, edição de legendas e exportação.
5. Testes em aparelhos reais, desempenho, tratamento de falhas e refinamento da interface.

Projeto independente; não incluímos código de repositórios terceiros sem revisar licença e compatibilidade.