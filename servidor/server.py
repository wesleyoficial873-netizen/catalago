"""
Servidor de clonagem de voz (Text-to-Speech com voz customizada).

Usa o Coqui XTTS v2 (modelo aberto e gratuito) para "clonar" qualquer voz
a partir de poucos segundos de áudio de referência, e falar qualquer texto
com essa voz.

Como usar:
    1. (Recomendado) Rode isso no Google Colab com GPU gratuita — veja o
       notebook `Servidor_Voz_Clone_Colab.ipynb` nesta mesma pasta, que já
       faz tudo isso automaticamente e te dá uma URL pública (ngrok).

    2. Ou rode localmente / em um servidor próprio com GPU (opcional, mas
       recomendado para velocidade):

        pip install -r requirements.txt
        python server.py

    O servidor sobe em 0.0.0.0:8000 com dois endpoints:
        GET  /health   -> {"status": "ok"}
        POST /tts      -> multipart/form-data com campos:
                             text     (texto a ser falado)
                             language (ex: "pt")
                             voice    (arquivo de áudio de referência)
                          resposta: áudio WAV gerado com a voz clonada

    Depois é só colar a URL pública (ex: a URL do ngrok) dentro do app
    Android, no campo "Servidor de clonagem de voz".
"""

import io
import os
import tempfile
import traceback

from fastapi import FastAPI, File, Form, HTTPException, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import Response

MODEL_NAME = "tts_models/multilingual/multi-dataset/xtts_v2"

app = FastAPI(title="Voz Clone - Servidor TTS")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

_tts_model = None


def get_model():
    """Carrega o modelo XTTS v2 sob demanda (lazy load) e mantém em cache."""
    global _tts_model
    if _tts_model is None:
        import torch
        from TTS.api import TTS

        device = "cuda" if torch.cuda.is_available() else "cpu"
        print(f"Carregando modelo {MODEL_NAME} em {device}...")
        _tts_model = TTS(MODEL_NAME).to(device)
        print("Modelo carregado com sucesso.")
    return _tts_model


@app.get("/health")
def health():
    return {"status": "ok"}


@app.post("/tts")
async def tts(
    text: str = Form(...),
    language: str = Form("pt"),
    voice: UploadFile = File(...),
):
    if not text.strip():
        raise HTTPException(status_code=400, detail="Campo 'text' vazio")

    try:
        model = get_model()

        # Salva o áudio de referência recebido em um arquivo temporário
        suffix = os.path.splitext(voice.filename or "voice.wav")[1] or ".wav"
        with tempfile.NamedTemporaryFile(suffix=suffix, delete=False) as ref_tmp:
            ref_tmp.write(await voice.read())
            ref_path = ref_tmp.name

        out_path = ref_path + "_out.wav"

        try:
            model.tts_to_file(
                text=text,
                speaker_wav=ref_path,
                language=language,
                file_path=out_path,
            )

            with open(out_path, "rb") as f:
                audio_bytes = f.read()

            return Response(content=audio_bytes, media_type="audio/wav")
        finally:
            for p in (ref_path, out_path):
                try:
                    os.remove(p)
                except OSError:
                    pass

    except HTTPException:
        raise
    except Exception as e:
        traceback.print_exc()
        raise HTTPException(status_code=500, detail=str(e))


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=8000)
