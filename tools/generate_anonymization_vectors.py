"""Genera app/src/sharedTest/assets/anonymization_vectors.json con el anonimizador de referencia (repo sms-anonymizer).

Uso: python -I -X utf8 tools/generate_anonymization_vectors.py RUTA/A/sms-anonymizer

Solo se usan las reglas de expresiones regulares y la lista de nombres: el reconocimiento de nombres con spaCy (NER) no se puede correr en el
teléfono, así que no forma parte del contrato que la app cumple. Todos los textos son sintéticos: nunca se ponen mensajes reales.
"""
import json
import re
import sys
from pathlib import Path

root = Path(sys.argv[1]).resolve()
sys.path.insert(0, str(root))

from sms_anonymizer.anonymize.known_names import compile_known_names, find_known_names  # noqa: E402
from sms_anonymizer.anonymize.regex_detectors import find_all  # noqa: E402
from sms_anonymizer.anonymize.rules import RULES_VERSION, TEXT_RULES  # noqa: E402
from sms_anonymizer.anonymize.spans import merge_spans  # noqa: E402

NAMES = ["Juan", "Perez", "Maria Jose", "Muñoz", "Ana"]
PATTERN = compile_known_names(NAMES)


def anonymize(text: str) -> str:
    spans = merge_spans(find_all(text) + find_known_names(text, PATTERN))
    out, cursor = [], 0
    for span in spans:
        out.append(text[cursor:span.start])
        out.append(span.placeholder)
        cursor = span.end
    out.append(text[cursor:])
    return "".join(out)


CASES = [
    ("sin_cambios", "Su pedido llega hoy entre las 9 y las 11."),
    ("url_con_esquema", "Ingrese a https://banco-seguro.example/login ahora"),
    ("url_sin_esquema", "Actualice en pagos-seguros.example/osc ya"),
    ("url_mayusculas", "Visite WWW.Ejemplo.COM/Promo hoy"),
    ("url_acortada", "Mire bit.ly/abc123 y gane"),
    ("dos_enlaces", "Escribanos a soporte@banco.com.pe o www.banco.com.pe"),
    ("correo", "Escriba a soporte@correo.example para ayuda"),
    ("correo_prioridad_sobre_url", "Contacto: ana.lopez@dominio.pe"),
    ("correo_con_acento", "Escriba a josé@correo.pe por favor"),
    ("telefono_con_mas_51", "Llame al +51 912 345 678 hoy"),
    ("telefono_con_guiones", "Comuniquese al 912-345-678"),
    ("telefono_pegado", "WhatsApp 912345678."),
    ("telefono_con_51_sin_mas", "Llame al 51912345678 ya"),
    ("numero_de_ocho_digitos_no_es_telefono", "Codigo 12345678 para su pedido"),
    ("dni", "Su DNI 12345678 fue validado"),
    ("dni_con_numero", "DNI N° 87654321 verificado"),
    ("ruc", "RUC: 20123456789 registrado"),
    ("ruc_persona_natural", "ruc 10123456789 activo"),
    ("numero_sin_palabra_clave", "Pedido 12345678 listo"),
    ("nombres_conocidos", "Hola Juan Perez, su pedido salio"),
    ("nombres_sin_distinguir_mayusculas_ni_acentos", "MARÍA JOSÉ llamo ayer"),
    ("nombre_con_enie_no_coincide", "Sr. Muñoz respondio"),
    ("nombre_dentro_de_otra_palabra", "Anabel y Ana vinieron"),
    ("nombre_gana_a_enlace", "Atte. Sr.Perez"),
    ("combinado", "Juan: llame al 912345678 o escriba a juan@x.pe, DNI 12345678, https://x.pe/a"),
    ("ya_anonimizado", "Ya anonimizado <URL> y <PHONE_NUMBER>"),
    ("montos_y_horas", "Pago de S/ 1.500,00 el 12.05 a las 3:30 p.m."),
    ("saltos_de_linea", "Hola\nllame al 912 345 678\nGracias"),
    ("vacio", ""),
]

doc = {
    "rules_version": RULES_VERSION,
    "rules": [{"name": r.name, "placeholder": r.placeholder, "priority": r.priority, "pattern": r.pattern} for r in TEXT_RULES if r.kind == "regex"],
    "known_names": NAMES,
    "cases": [{"id": cid, "text": text, "expected": anonymize(text)} for cid, text in CASES],
}
out = Path(__file__).resolve().parent.parent / "app" / "src" / "sharedTest" / "assets" / "anonymization_vectors.json"
out.write_text(json.dumps(doc, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(f"{len(CASES)} casos, reglas {RULES_VERSION}, escrito en {out}")
