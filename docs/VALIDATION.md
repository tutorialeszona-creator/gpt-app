# Validación de Mi Departamento 0.2.0

## Entorno

Linux x86_64, Eclipse Temurin JDK 21.0.13, Gradle 8.11.1, Android SDK Platform 35 y Build Tools 35.0.0. Las herramientas se descargaron de sus publicaciones oficiales y se verificaron sus checksums; se mantuvo la validación TLS.

En el entorno cloud:

```sh
cd /workspace/gpt-app
python3 scripts/cloud_setup.py
# Para comandos posteriores:
source scripts/cloud-env.sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

El script prepara o reutiliza las herramientas en `/workspace/toolchains`, conserva `local.properties` si ya existe y ejecuta pruebas, Lint y construcción del APK. Cada tarea cloud ya está aislada: usá este checkout, sin crear otro worktree salvo pedido explícito.

## Cobertura automatizada

- `FinanceTest`: importes y decimales, conversión/redondeo, frecuencia entre años, vencimientos, cotización faltante y cálculo sin duplicar pagos.
- `RepositoryTest` con Room real bajo Robolectric: generación concurrente idempotente, siete conceptos e importes exactos, estimados y fechas vacías, registro y anulación de pagos, no resucitar boletas eliminadas, frecuencia bimestral, preservación de meses anteriores, lectura desde otra instancia de la base en disco, edición/borrado y exportación JSON con un comprobante Base64.
- `AppSmokeTest` con Compose bajo Robolectric: ingreso por formulario, rechazo de importe inválido y creación del ingreso correcto.
- `MainActivityTest`: arranque real de la actividad, carga de la base y navegación a Boletas y Configuración, con desplazamiento en pantallas pequeñas.

Resultado final en esta instancia: **21 pruebas ejecutadas, 21 correctas, 0 fallidas, 0 errores y 0 omitidas**. `testDebugUnitTest`, `lintDebug` y `assembleDebug` completados correctamente. Lint no informa errores ni advertencias. Se verificó la firma del APK y que coincide con la versión 0.1; el identificador sigue siendo `ar.com.midepartamento`, ahora con `versionCode=2`.

APK de desarrollo: `downloads/Mi-Departamento-v0.2.apk` (61.224.586 bytes).
SHA-256: `434678e13be1d53b6c10a0b1d3a53b00106799f46d647eaa0881cf284761e455`.

Se comprobó que el APK incluye los modelos OCR y sus bibliotecas nativas y que no declara el permiso `INTERNET`. Esto verifica que las herramientas y el modelo vienen empaquetados, pero no demuestra la calidad del reconocimiento en un teléfono.

Pruebas nuevas: importes de tickets argentinos, fechas inválidas y ausentes, subtotal/IVA/descuentos/vuelto, totales por rubro sin duplicar productos, revisión y corrección antes de confirmar, base Room actualizada desde el esquema 1 real con gastos preservados, productos persistidos al reabrir la base, rechazo de la misma imagen duplicada, copia mediante FileProvider y respaldo JSON 2 con fotos y detalle.
La configuración reutilizable de instalación, arranque y ubicación del checkout se guardó en el borrador del entorno. Guardar el borrador no publica el snapshot; la publicación y la validación en una nueva tarea quedan fuera de esta comprobación.

## Prueba manual en Android Studio o teléfono (pendiente)

No se ha probado aún en un teléfono ni en un emulador Android. Este entorno no dispone de `/dev/kvm`. Robolectric ejecuta pruebas de Android y Compose en la JVM; no sustituye una comprobación visual y funcional en un dispositivo.

Con una instalación nueva:

1. Inicio: previsto ARS 668.000, real ARS 0, siete boletas pendientes, cero pagadas. USD 1.000 y advertencia de cotización faltante. No debe haber fechas de vencimiento.
2. Configuración: ingresar cotización **1000**. Deben verse ingresos ARS 1.000.000 y disponible previsto ARS 332.000.
3. Boletas: electricidad debe figurar estimada. Ingresar importe real **48000**, marcar pagada y elegir la fecha efectiva. Esperado: previstos ARS 666.000, reales ARS 48.000, disponible ARS 334.000. No agregar ese pago como gasto personal.
4. Movimientos: agregar supermercado **1250**. Esperado: previstos ARS 667.250, reales ARS 49.250 y disponible ARS 332.750.
5. Cerrar y abrir la app. Los registros, la cotización y los pagos deben conservarse.
6. Volver al mismo mes varias veces: no debe haber boletas duplicadas. Eliminar una boleta y regresar: no debe reaparecer.
7. Adjuntar un PDF o imagen, cerrar la app y volver a abrir el comprobante. Exportar la copia JSON mediante el selector de archivos de Android.
8. Cambiar frecuencia de un concepto y abrir un mes todavía no generado. Confirmar que se aplica la frecuencia nueva y que las boletas anteriores se conservan.
9. Probar pantalla pequeña, tamaño de fuente grande, teclado, rotación, navegación inferior y selector de fechas.

## Pendientes conocidos

Importación/restauración de copias desde la interfaz, notificaciones, pagos parciales y cotización histórica por movimiento. Exportar una copia funciona como respaldo portable, pero esta versión todavía no incluye su restauración automática.


## Comprobaciones manuales pendientes para v0.2

- Instalar la actualización encima de v0.1 en un teléfono y verificar datos existentes.
- Crear compras de supermercado, verdulería y carnicería, agregar productos y comprobar el total del mes. Editar y eliminar compras y revisar el recálculo.
- Con el teléfono sin conexión, fotografiar tickets impresos reales, importar imágenes de galería y revisar los datos sugeridos. Probar cancelación de cámara/galería y fechas o totales no reconocidos.
- Revisar un ticket con descuentos: la suma de productos puede ser distinta del total, y sólo el total debe sumarse al gasto.
- Importar la misma foto dos veces: la segunda confirmación debe rechazarse. Probar abrir la foto guardada después de cerrar la app y eliminar la original de la galería.

No se ejecutó el motor OCR nativo ni la cámara en un teléfono o emulador Android real en este entorno. Las pruebas de lectura parten de texto y las de importación ejercitan archivos y revisión; no se presentan como validación del reconocimiento visual real.
