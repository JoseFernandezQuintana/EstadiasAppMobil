"""
Proveedores de IA del backend CECAPI (Módulo 3).

Cada proveedor sabe preguntarle a UNA API. El orden de la lista PROVEEDORES es el
orden del fallback: si el primero falla (sin cuota, caído, sin clave), se intenta
el siguiente. Las claves se leen SOLO de variables de entorno (archivo .env local),
nunca van escritas en el código.
"""

import logging
import os

import uso

log = logging.getLogger("cecapi.proveedores")

# Cómo debe responder la IA: la persona ESCUCHA la respuesta, no la lee.
INSTRUCCIONES = (
    "Eres el asistente de voz de CECAPI para personas con discapacidad visual. "
    "Responde siempre en español de México, en frases cortas y claras, "
    "como si hablaras en voz alta: máximo tres o cuatro oraciones. "
    "No uses markdown, viñetas, tablas, emojis ni enlaces. "
    "Si no sabes algo, dilo con honestidad."
)

TIEMPO_LIMITE_S = float(os.getenv("IA_TIEMPO_LIMITE_S", "20"))


class ProveedorNoDisponible(Exception):
    """El proveedor no está configurado o falló; hay que probar el siguiente."""


def _armar_prompt(pregunta: str, contexto: list[str]) -> str:
    if not contexto:
        return pregunta
    historial = "\n".join(contexto)
    return f"Conversación anterior:\n{historial}\n\nPregunta actual: {pregunta}"


class Gemini:
    nombre = "gemini"

    def __init__(self) -> None:
        self.clave = os.getenv("GEMINI_API_KEY", "").strip()
        self.modelo = os.getenv("GEMINI_MODEL", "gemini-3.8-flash")
        self._cliente = None

    @property
    def configurado(self) -> bool:
        return bool(self.clave)

    def _cliente_listo(self):
        if self._cliente is None:
            from google import genai
            from google.genai import types

            self._cliente = genai.Client(
                api_key=self.clave,
                http_options=types.HttpOptions(timeout=int(TIEMPO_LIMITE_S * 1000)),
            )
        return self._cliente

    def preguntar(self, pregunta: str, contexto: list[str]) -> str:
        from google.genai import types

        respuesta = self._cliente_listo().models.generate_content(
            model=self.modelo,
            contents=_armar_prompt(pregunta, contexto),
            config=types.GenerateContentConfig(system_instruction=INSTRUCCIONES),
        )
        texto = (respuesta.text or "").strip()
        if not texto:
            raise ProveedorNoDisponible("Gemini respondió vacío")
        return texto


class Groq:
    nombre = "groq"

    def __init__(self) -> None:
        self.clave = os.getenv("GROQ_API_KEY", "").strip()
        self.modelo = os.getenv("GROQ_MODEL", "openai/gpt-oss-20b")
        self._cliente = None

    @property
    def configurado(self) -> bool:
        return bool(self.clave)

    def _cliente_listo(self):
        if self._cliente is None:
            from groq import Groq as ClienteGroq

            # max_retries=0: si falla, preferimos pasar al siguiente proveedor ya.
            self._cliente = ClienteGroq(api_key=self.clave, timeout=TIEMPO_LIMITE_S, max_retries=0)
        return self._cliente

    def preguntar(self, pregunta: str, contexto: list[str]) -> str:
        respuesta = self._cliente_listo().chat.completions.create(
            model=self.modelo,
            messages=[
                {"role": "system", "content": INSTRUCCIONES},
                {"role": "user", "content": _armar_prompt(pregunta, contexto)},
            ],
        )
        texto = (respuesta.choices[0].message.content or "").strip()
        if not texto:
            raise ProveedorNoDisponible("Groq respondió vacío")
        return texto


# Orden del fallback: primero Gemini, si falla Groq.
PROVEEDORES = [Gemini(), Groq()]


def configurados() -> list:
    return [p for p in PROVEEDORES if p.configurado]


def preguntar_con_respaldo(pregunta: str, contexto: list[str]) -> tuple[str, str]:
    """Devuelve (respuesta, nombre_del_proveedor). Lanza ProveedorNoDisponible si todos fallan."""
    for proveedor in configurados():
        if uso.agotado(proveedor.nombre):
            log.info("%s ya usó su límite de hoy; se salta", proveedor.nombre)
            continue
        uso.registrar(proveedor.nombre)
        try:
            return proveedor.preguntar(pregunta, contexto), proveedor.nombre
        except Exception as error:  # cuota agotada, red, clave inválida, modelo retirado...
            # Solo el tipo de error: el mensaje podría incluir la pregunta del usuario.
            log.warning("Falló %s (%s); probando el siguiente", proveedor.nombre, type(error).__name__)
    raise ProveedorNoDisponible("Ningún proveedor de IA respondió")
