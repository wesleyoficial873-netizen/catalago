# 🎙️ Voz Clone — App Android de clonagem de voz

App Android que permite digitar um texto e ouvi-lo falado com **qualquer voz**
que você anexar (arquivo de áudio) ou gravar direto no celular.

A clonagem de voz de verdade (usar o timbre de qualquer pessoa) exige um modelo
de IA relativamente pesado, que roda melhor com GPU. Por isso a arquitetura é:

```
[App Android]  --texto + áudio de referência-->  [Servidor de IA (Coqui XTTS v2)]
[App Android]  <---------- áudio gerado ---------  [Servidor de IA]
```

O app é só o "cliente": você digita o texto, anexa/grava a voz, e ele manda
tudo para um servidor que faz a clonagem e devolve o áudio pronto. O servidor
é **gratuito e de código aberto** (Coqui XTTS v2) e você mesmo hospeda (o jeito
mais fácil é usar o Google Colab, que dá GPU de graça — veja abaixo).

## 📁 Estrutura deste repositório

```
android/     -> projeto Android (Kotlin) do app "Voz Clone"
servidor/    -> servidor de clonagem de voz (FastAPI + Coqui XTTS v2)
  server.py                          -> código do servidor (para rodar em qualquer lugar)
  requirements.txt                   -> dependências Python do servidor
  Servidor_Voz_Clone_Colab.ipynb     -> notebook pronto para rodar no Google Colab (grátis)
.github/workflows/build-apk.yml      -> compila o APK automaticamente a cada push
```

## 1) Como gerar o APK

O APK é compilado automaticamente pelo **GitHub Actions** a cada push nesta
branch. Depois que o workflow "Build APK" terminar (aba **Actions** do
repositório no GitHub), você encontra o instalador em dois lugares:

- Aba **Actions** → o run mais recente → seção **Artifacts** → `voz-clone-apk`
- Aba **Releases** → a release mais recente `apk-build-N` → arquivo `app-debug.apk`

Baixe o `app-debug.apk` no seu celular Android e instale (talvez seja preciso
permitir "instalar apps de fontes desconhecidas" nas configurações do
Android).

Se quiser compilar manualmente (com Android Studio ou linha de comando com o
Android SDK instalado):

```bash
cd android
./gradlew assembleDebug
# APK gerado em: android/app/build/outputs/apk/debug/app-debug.apk
```

## 2) Como subir o servidor de voz (gratuito, via Google Colab)

1. Abra o arquivo `servidor/Servidor_Voz_Clone_Colab.ipynb` no
   [Google Colab](https://colab.research.google.com/) (Arquivo → Fazer
   upload de notebook, ou arraste o arquivo).
2. No menu **Ambiente de execução → Alterar tipo de ambiente de execução**,
   escolha **GPU (T4)** — é grátis.
3. Crie uma conta grátis em https://dashboard.ngrok.com/signup e copie seu
   **Authtoken**.
4. Cole o token na célula indicada e rode todas as células, de cima para
   baixo.
5. No final, o notebook mostra uma URL pública tipo:
   `🌍 URL pública do servidor: https://xxxx.ngrok-free.app`
6. Copie essa URL.

⚠️ O servidor só fica no ar enquanto o notebook do Colab estiver aberto e
rodando. Se fechar a aba, precisa rodar de novo (e a URL muda). Isso é uma
limitação do Colab gratuito — para algo permanente, você pode hospedar
`servidor/server.py` em uma VM/servidor próprio com GPU.

## 3) Como usar o app

1. Abra o app **Voz Clone** no celular.
2. No campo **"Servidor de clonagem de voz"**, cole a URL do ngrok (do passo
   anterior) e toque em **"Testar conexão"** para confirmar que está tudo
   certo.
3. Em **"Voz de referência"**, toque em **"Anexar áudio"** para escolher um
   arquivo de áudio de alguém falando (o ideal são uns 10-20 segundos de fala
   limpa, sem ruído/música de fundo), ou toque em **"Gravar voz"** para
   gravar na hora pelo microfone do celular.
4. Digite o texto que você quer que essa voz fale.
5. Toque em **"Gerar fala com essa voz"**. Aguarde alguns segundos (a
   primeira geração demora mais, pois o modelo é carregado na memória).
6. Use **"Ouvir resultado"**, **"Salvar áudio"** (vai para a pasta Downloads)
   ou **"Compartilhar"**.

## Tecnologias usadas

- **App**: Kotlin, Material Design 3, OkHttp, Coroutines.
- **Servidor de IA**: [Coqui XTTS v2](https://github.com/coqui-ai/TTS) — modelo
  open-source de clonagem de voz multilíngue (suporta português).
- **Túnel público gratuito**: [ngrok](https://ngrok.com/), usado dentro do
  Colab para expor o servidor para a internet.

## Avisos importantes

- Use isso de forma ética e legal: só clone a voz de pessoas que autorizaram,
  e nunca para enganar, fraudar ou se passar por alguém sem consentimento.
- O servidor gratuito via Colab não é para uso profissional/comercial
  contínuo — é ótimo para testar e usar pessoalmente.
