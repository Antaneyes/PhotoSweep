# Contribuir a PhotoSweep

Gracias por ayudar a mejorar el proyecto. PhotoSweep modifica una fototeca real
mediante endpoints no documentados, así que cualquier cambio debe priorizar la
seguridad de los datos.

## Flujo recomendado

1. Crea una rama a partir de `main`.
2. Realiza cambios pequeños y explicables.
3. Ejecuta `./gradlew test lint assembleDebug`.
4. Prueba primero con una cuenta y elementos desechables.
5. Abre un pull request explicando el comportamiento y las pruebas realizadas.

## Reglas de seguridad

- No adjuntes fotos personales, cookies, tokens, URLs autenticadas,
  identificadores de cuenta ni registros completos de sesión.
- No confirmes `signing.properties`, claves `*.jks`/`*.keystore`, APKs o
  contraseñas.
- No elimines la doble confirmación de la cesta.
- No añadas telemetría, analítica, publicidad o servidores sin documentar con
  precisión el nuevo flujo de datos y obtener consentimiento explícito.
- Los cambios en los RPC internos deben probar indexación, miniaturas, vídeo,
  cesta, restauración y verificación de ausencia con contenido desechable.

Los fallos que puedan exponer una cuenta o provocar borrados inesperados deben
comunicarse siguiendo [SECURITY.md](SECURITY.md), no mediante un issue público.
