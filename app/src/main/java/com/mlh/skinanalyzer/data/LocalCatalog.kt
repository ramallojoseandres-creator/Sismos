package com.mlh.skinanalyzer.data

/**
 * Local content catalog that replaces OEM cloud endpoints:
 * - app/article/list (care suggestions)
 * - app/recommendation/product (product list)
 * - shop / login cloud profile → ClinicProfile
 *
 * No network. All Spanish clinical-support copy for Dra. MLH.
 */
object LocalCatalog {

    private val defaultIndicators = listOf(
        "sebum" to "Sebo / grasa",
        "pores" to "Poros dilatados",
        "pigmentation" to "Manchas / pigmentación",
        "wrinkles" to "Arrugas y líneas finas",
        "acne" to "Acné / inflamación",
        "blackheads" to "Puntos negros",
        "dark_circles" to "Ojeras",
        "moisture" to "Hidratación",
        "texture" to "Textura",
        "sensitivity" to "Sensibilidad",
        "uv_spots" to "Manchas UV",
        "deep_pigment" to "Pigmento profundo",
        "deep_acne" to "Acné profundo",
        "collagen" to "Pérdida de colágeno",
    )

    suspend fun ensureSeeded(db: AppDatabase) {
        if (db.clinicDao().get() == null) {
            db.clinicDao().upsert(ClinicProfile())
        }
        if (db.indicatorPrefDao().listAll().isEmpty()) {
            db.indicatorPrefDao().upsertAll(
                defaultIndicators.mapIndexed { i, (key, _) ->
                    IndicatorPref(key = key, enabled = true, sortOrder = i)
                },
            )
        }
        if (db.careGuideDao().count() == 0) {
            db.careGuideDao().insertAll(careGuides())
        }
        if (db.productDao().count() == 0) {
            db.productDao().insertAll(products())
        }
    }

    suspend fun seed(db: AppDatabase) = ensureSeeded(db)

    private fun careGuides(): List<CareGuide> = listOf(
        CareGuide(metricKey = "sebum", title = "Control de sebo", layer = "superficial",
            body = "Limpiar mañana y noche con gel suave. Evitar alcoholes fuertes. Incorporar niacinamida 4–5% y protector solar matificante."),
        CareGuide(metricKey = "pores", title = "Cuidado de poros", layer = "superficial",
            body = "Ácidos BHA (ácido salicílico) 1–2 noches/semana. No extruir. Retinoides suaves y fotoprotección diaria."),
        CareGuide(metricKey = "pigmentation", title = "Manchas superficiales", layer = "superficial",
            body = "Vitamina C por la mañana, FPS 50+ y despigmentantes (ácido tranexámico / niacinamida). Evitar exposiciones intensas."),
        CareGuide(metricKey = "wrinkles", title = "Líneas finas", layer = "superficial",
            body = "Retinol progresivo, péptidos e hidratación con ácido hialurónico. Dormir de lado/espalda y FPS diario."),
        CareGuide(metricKey = "acne", title = "Acné inflamatorio", layer = "superficial",
            body = "No manipular lesiones. Peróxido de benzoilo o adapaleno según tolerancia. Consultar si hay nódulos."),
        CareGuide(metricKey = "blackheads", title = "Puntos negros", layer = "superficial",
            body = "Limpieza enzimática + BHA. Evitar bandas agresivas. Sellado ligero de poros al finalizar."),
        CareGuide(metricKey = "dark_circles", title = "Ojeras", layer = "superficial",
            body = "Descanso, cafeína tópica suave y corregir déficit de hierro si aplica. Contorno con péptidos."),
        CareGuide(metricKey = "moisture", title = "Hidratación", layer = "superficial",
            body = "Limpiar sin espuma agresiva. Humectantes (glicerina, HA) + oclusivo ligero. Beber agua y humidificar ambiente seco."),
        CareGuide(metricKey = "texture", title = "Textura irregular", layer = "superficial",
            body = "Exfoliación química suave (AHA/PHA). Evitar scrubs. Retinoides y fotoprotección."),
        CareGuide(metricKey = "sensitivity", title = "Sensibilidad / barrera", layer = "profunda",
            body = "Minimalismo: ceramidas, pantenol, centella. Suspender activos fuertes 7–10 días. Agua tibia."),
        CareGuide(metricKey = "uv_spots", title = "Daño UV latente", layer = "profunda",
            body = "FPS de amplio espectro todo el año. Antioxidantes y revisar manchas con luz Wood/UV en controles."),
        CareGuide(metricKey = "deep_pigment", title = "Pigmento profundo", layer = "profunda",
            body = "Protocolos despigmentantes supervisados. Evitar irritación que reactive melanocitos."),
        CareGuide(metricKey = "deep_acne", title = "Acné profundo", layer = "profunda",
            body = "Evaluar tratamiento médico (tópico/oral). No extracciones profundas en cabina sin protocolo."),
        CareGuide(metricKey = "collagen", title = "Soporte de colágeno", layer = "profunda",
            body = "Retinoides, vitamina C, radiofrecuencia/estimulación según indicación clínica y fotoprotección estricta."),
    )

    private fun products(): List<ProductRec> = listOf(
        ProductRec(metricKey = "sebum", name = "Gel limpiador sebo-regulador", category = "Limpieza",
            description = "Surfactantes suaves + zinc PCA.", howToUse = "AM/PM sobre rostro húmedo, 30 s."),
        ProductRec(metricKey = "sebum", name = "Sérum niacinamida 5%", category = "Tratamiento",
            description = "Reduce brillo y refina poros.", howToUse = "AM tras limpieza, antes del FPS."),
        ProductRec(metricKey = "pores", name = "Tónico BHA 1%", category = "Tratamiento",
            description = "Ácido salicílico para poros y comedones.", howToUse = "PM, 2–3 noches/semana."),
        ProductRec(metricKey = "pigmentation", name = "Vitamina C estable 15%", category = "Antioxidante",
            description = "Ilumina y potencia el FPS.", howToUse = "AM sobre piel seca."),
        ProductRec(metricKey = "pigmentation", name = "FPS mineral 50+", category = "Protección",
            description = "Amplio espectro, uso diario.", howToUse = "AM y reaplicar cada 2–3 h al sol."),
        ProductRec(metricKey = "wrinkles", name = "Retinol encapsulado 0.3%", category = "Noche",
            description = "Estimula renovación y colágeno.", howToUse = "PM, noches alternas; buffer con crema."),
        ProductRec(metricKey = "acne", name = "Gel adapaleno 0.1%", category = "Tratamiento",
            description = "Comedolítico e antiinflamatorio.", howToUse = "PM capa fina; hidratar después."),
        ProductRec(metricKey = "moisture", name = "Crema barrera ceramidas", category = "Hidratación",
            description = "Restaura lípidos de la barrera.", howToUse = "AM/PM según necesidad."),
        ProductRec(metricKey = "sensitivity", name = "Sérum centella + pantenol", category = "Calmante",
            description = "Reduce rojeces y ardor.", howToUse = "AM/PM; pausar ácidos."),
        ProductRec(metricKey = "uv_spots", name = "Antioxidante polifenoles", category = "Prevención",
            description = "Complementa el FPS frente a radicales.", howToUse = "AM bajo el protector."),
        ProductRec(metricKey = "collagen", name = "Crema péptidos firming", category = "Antiedad",
            description = "Soporte de firmeza y elasticidad.", howToUse = "AM/PM sobre sérum."),
        ProductRec(metricKey = "dark_circles", name = "Contorno cafeína", category = "Contorno",
            description = "Descongestiona y alisa.", howToUse = "AM/PM con dedo anular."),
    )
}
