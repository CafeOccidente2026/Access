"""
Exporta TODO el codigo VBA, las macros, y los formularios (con sus
"Origen del control") de un .accdb a archivos de texto planos.
Cafe Occidente - migracion Access -> sistema nuevo.
"""

import os
import win32com.client

ARCHIVO_ACCESS = r"C:\Users\Usuario\Documents\ElTambo2026.accdb"
CARPETA_SALIDA = r"C:\Users\Usuario\Documents\Migracion de Access\docs\legacy-vba-export\eltambo"

for sub in ("modules", "macros", "forms", "reports"):
    os.makedirs(os.path.join(CARPETA_SALIDA, sub), exist_ok=True)

app = win32com.client.Dispatch("Access.Application")
app.OpenCurrentDatabase(ARCHIVO_ACCESS)

# 1. Todo el codigo VBA (los "Private Sub" que ya me has pasado a mano)
vbproj = app.VBE.VBProjects.Item(1)
for comp in vbproj.VBComponents:
    n = comp.CodeModule.CountOfLines
    if n > 0:
        codigo = comp.CodeModule.Lines(1, n)
        with open(os.path.join(CARPETA_SALIDA, "modules", f"{comp.Name}.bas"), "w", encoding="utf-8") as f:
            f.write(codigo)
    print(f"Modulo: {comp.Name}")

# 2. Todas las macros (como "Para actualizar No anuncios corrf")
for macro in app.CurrentProject.AllMacros:
    try:
        app.SaveAsText(4, macro.Name, os.path.join(CARPETA_SALIDA, "macros", f"{macro.Name}.txt"))
        print(f"Macro: {macro.Name}")
    except Exception as e:
        print(f"Error en macro {macro.Name}: {e}")

# 3. Todos los formularios (esto incluye el "Origen del control" de cada
#    campo como texto - resuelve lo de Texto15/Texto17 sin clics manuales)
for frm in app.CurrentProject.AllForms:
    try:
        app.SaveAsText(2, frm.Name, os.path.join(CARPETA_SALIDA, "forms", f"{frm.Name}.txt"))
        print(f"Formulario: {frm.Name}")
    except Exception as e:
        print(f"Error en formulario {frm.Name}: {e}")

# 4. Reportes (por si acaso, ej. la Factura que se imprime)
for rep in app.CurrentProject.AllReports:
    try:
        app.SaveAsText(3, rep.Name, os.path.join(CARPETA_SALIDA, "reports", f"{rep.Name}.txt"))
    except Exception as e:
        print(f"Error en reporte {rep.Name}: {e}")

app.CloseCurrentDatabase()
app.Quit()
print("Listo.")