import os
import google.generativeai as genai
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

# Configura Gemini con la API key del entorno
genai.configure(api_key=os.environ["GEMINI_API_KEY"])
model = genai.GenerativeModel("gemini-2.0-flash")

app = FastAPI()

class Pregunta(BaseModel):
    pregunta: str
    contexto: list[str] = []

@app.get("/")
def raiz():
    return {"status": "CECAPI backend activo"}

@app.post("/preguntar")
async def responder(body: Pregunta):
    try:
        historial = "\n".join(body.contexto)
        prompt = f"{historial}\nUsuario: {body.pregunta}" if historial else body.pregunta
        respuesta = model.generate_content(prompt)
        return {"respuesta": respuesta.text}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))