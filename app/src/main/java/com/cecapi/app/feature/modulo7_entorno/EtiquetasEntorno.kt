package com.cecapi.app.feature.modulo7_entorno

data class NombreEs(
    val texto: String,
    val conArticulo: String,
    val esFemenino: Boolean = false,
)

object EtiquetasEntorno {

    private val traducciones = mapOf(
        // Mobiliario / Hogar
        "chair" to NombreEs("silla", "una silla", true),
        "armchair" to NombreEs("sillón", "un sillón", false),
        "office chair" to NombreEs("silla de oficina", "una silla de oficina", true),
        "table" to NombreEs("mesa", "una mesa", true),
        "desk" to NombreEs("escritorio", "un escritorio", false),
        "coffee table" to NombreEs("mesa de centro", "una mesa de centro", true),
        "dining table" to NombreEs("mesa de comedor", "una mesa de comedor", true),
        "couch" to NombreEs("sofá", "un sofá", false),
        "sofa" to NombreEs("sofá", "un sofá", false),
        "bed" to NombreEs("cama", "una cama", true),
        "shelf" to NombreEs("estante", "un estante", false),
        "bookcase" to NombreEs("librero", "un librero", false),
        "cabinet" to NombreEs("mueble", "un mueble", false),
        "cupboard" to NombreEs("alacena", "una alacena", true),
        "wardrobe" to NombreEs("ropero", "un ropero", false),
        "door" to NombreEs("puerta", "una puerta", true),
        "window" to NombreEs("ventana", "una ventana", true),
        "lamp" to NombreEs("lámpara", "una lámpara", true),
        "lighting" to NombreEs("lámpara", "una lámpara", true),
        "mirror" to NombreEs("espejo", "un espejo", false),
        "clock" to NombreEs("reloj", "un reloj", false),
        "wall clock" to NombreEs("reloj de pared", "un reloj de pared", false),
        "pillow" to NombreEs("almohada", "una almohada", true),
        "cushion" to NombreEs("cojín", "un cojín", false),
        "curtain" to NombreEs("cortina", "una cortina", true),
        "carpet" to NombreEs("alfombra", "una alfombra", true),
        "rug" to NombreEs("tapete", "un tapete", false),

        // Electrónicos / Dispositivos
        "laptop" to NombreEs("laptop", "una laptop", true),
        "netbook" to NombreEs("laptop", "una laptop", true),
        "computer keyboard" to NombreEs("teclado", "un teclado", false),
        "keyboard" to NombreEs("teclado", "un teclado", false),
        "computer monitor" to NombreEs("monitor", "un monitor", false),
        "display device" to NombreEs("pantalla", "una pantalla", true),
        "screen" to NombreEs("pantalla", "una pantalla", true),
        "television" to NombreEs("televisor", "un televisor", false),
        "tv" to NombreEs("televisor", "un televisor", false),
        "computer" to NombreEs("computadora", "una computadora", true),
        "desktop computer" to NombreEs("computadora", "una computadora", true),
        "mouse" to NombreEs("mouse", "un mouse", false),
        "computer mouse" to NombreEs("mouse", "un mouse", false),
        "mobile phone" to NombreEs("teléfono", "un teléfono", false),
        "smartphone" to NombreEs("teléfono", "un teléfono", false),
        "telephone" to NombreEs("teléfono", "un teléfono", false),
        "cell phone" to NombreEs("teléfono celular", "un teléfono celular", false),
        "cellular network" to NombreEs("teléfono", "un teléfono", false),
        "gadget" to NombreEs("dispositivo", "un dispositivo", false),
        "communication device" to NombreEs("dispositivo", "un dispositivo", false),
        "headphones" to NombreEs("audífonos", "unos audífonos", false),
        "headset" to NombreEs("audífonos", "unos audífonos", false),
        "audio equipment" to NombreEs("equipo de audio", "un equipo de audio", false),
        "camera" to NombreEs("cámara", "una cámara", true),
        "remote control" to NombreEs("control remoto", "un control remoto", false),
        "speaker" to NombreEs("bocina", "una bocina", true),
        "loudspeaker" to NombreEs("bocina", "una bocina", true),
        "tablet" to NombreEs("tableta", "una tableta", true),
        "microphone" to NombreEs("micrófono", "un micrófono", false),
        "cable" to NombreEs("cable", "un cable", false),
        "charger" to NombreEs("cargador", "un cargador", false),

        // Objetos personales y recipientes
        "backpack" to NombreEs("mochila", "una mochila", true),
        "bag" to NombreEs("bolsa", "una bolsa", true),
        "handbag" to NombreEs("bolsa", "una bolsa", true),
        "suitcase" to NombreEs("maleta", "una maleta", true),
        "wallet" to NombreEs("cartera", "una cartera", true),
        "purse" to NombreEs("monedero", "un monedero", false),
        "bottle" to NombreEs("botella", "una botella", true),
        "water bottle" to NombreEs("botella de agua", "una botella de agua", true),
        "glass bottle" to NombreEs("botella de vidrio", "una botella de vidrio", true),
        "cup" to NombreEs("taza", "una taza", true),
        "mug" to NombreEs("taza", "una taza", true),
        "coffee cup" to NombreEs("taza de café", "una taza de café", true),
        "glass" to NombreEs("vaso", "un vaso", false),
        "drinkware" to NombreEs("vaso", "un vaso", false),
        "glasses" to NombreEs("lentes", "unos lentes", false),
        "eyewear" to NombreEs("lentes", "unos lentes", false),
        "sunglasses" to NombreEs("lentes de sol", "unos lentes de sol", false),
        "book" to NombreEs("libro", "un libro", false),
        "publication" to NombreEs("libro", "un libro", false),
        "bookcover" to NombreEs("libro", "un libro", false),
        "paper" to NombreEs("papel", "un papel", false),
        "notebook" to NombreEs("cuaderno", "un cuaderno", false),
        "pen" to NombreEs("pluma", "una pluma", true),
        "pencil" to NombreEs("lápiz", "un lápiz", false),
        "writing implement" to NombreEs("pluma", "una pluma", true),
        "key" to NombreEs("llave", "una llave", true),
        "keys" to NombreEs("llaves", "unas llaves", true),
        "box" to NombreEs("caja", "una caja", true),
        "carton" to NombreEs("caja de cartón", "una caja de cartón", true),
        "umbrella" to NombreEs("paraguas", "un paraguas", false),

        // Ropa y calzado
        "shirt" to NombreEs("camisa", "una camisa", true),
        "t-shirt" to NombreEs("playera", "una playera", true),
        "top" to NombreEs("prenda", "una prenda", true),
        "pants" to NombreEs("pantalón", "un pantalón", false),
        "trousers" to NombreEs("pantalón", "un pantalón", false),
        "jeans" to NombreEs("pantalón de mezclilla", "un pantalón de mezclilla", false),
        "shorts" to NombreEs("shorts", "unos shorts", false),
        "jacket" to NombreEs("chamarra", "una chamarra", true),
        "coat" to NombreEs("abrigo", "un abrigo", false),
        "sweater" to NombreEs("suéter", "un suéter", false),
        "shoe" to NombreEs("zapato", "un zapato", false),
        "shoes" to NombreEs("zapatos", "unos zapatos", false),
        "footwear" to NombreEs("zapato", "un zapato", false),
        "sneakers" to NombreEs("tenis", "unos tenis", false),
        "hat" to NombreEs("sombrero", "un sombrero", false),
        "cap" to NombreEs("gorra", "una gorra", true),
        "dress" to NombreEs("vestido", "un vestido", false),

        // Utensilios y cocina
        "plate" to NombreEs("plato", "un plato", false),
        "dish" to NombreEs("plato", "un plato", false),
        "tableware" to NombreEs("utensilio de cocina", "un utensilio de cocina", false),
        "spoon" to NombreEs("cuchara", "una cuchara", true),
        "fork" to NombreEs("tenedor", "un tenedor", false),
        "knife" to NombreEs("cuchillo", "un cuchillo", false),
        "bowl" to NombreEs("tazón", "un tazón", false),
        "pan" to NombreEs("sartén", "un sartén", false),
        "pot" to NombreEs("olla", "una olla", true),

        // Comida y plantas
        "apple" to NombreEs("manzana", "una manzana", true),
        "banana" to NombreEs("plátano", "un plátano", false),
        "orange" to NombreEs("naranja", "una naranja", true),
        "fruit" to NombreEs("fruta", "una fruta", true),
        "food" to NombreEs("alimento", "un alimento", false),
        "bread" to NombreEs("pan", "un pan", false),
        "plant" to NombreEs("planta", "una planta", true),
        "houseplant" to NombreEs("planta de interior", "una planta de interior", true),
        "flower" to NombreEs("flor", "una flor", true),
        "flowerpot" to NombreEs("maceta", "una maceta", true),
        "tree" to NombreEs("árbol", "un árbol", false),

        // Vehículos y transporte
        "car" to NombreEs("auto", "un auto", false),
        "vehicle" to NombreEs("vehículo", "un vehículo", false),
        "bicycle" to NombreEs("bicicleta", "una bicicleta", true),
        "bike" to NombreEs("bicicleta", "una bicicleta", true),
        "motorcycle" to NombreEs("motocicleta", "una motocicleta", true),
        "bus" to NombreEs("camión", "un camión", false),

        // Personas y animales (antes no estaban: una persona o mascota enfrente no se decía nada de ella)
        "person" to NombreEs("persona", "una persona", true),
        "human" to NombreEs("persona", "una persona", true),
        "man" to NombreEs("hombre", "un hombre", false),
        "woman" to NombreEs("mujer", "una mujer", true),
        "boy" to NombreEs("niño", "un niño", false),
        "girl" to NombreEs("niña", "una niña", true),
        "child" to NombreEs("niño", "un niño", false),
        "animal" to NombreEs("animal", "un animal", false),
        "dog" to NombreEs("perro", "un perro", false),
        "cat" to NombreEs("gato", "un gato", false),
        "bird" to NombreEs("pájaro", "un pájaro", false),
        "fish" to NombreEs("pez", "un pez", false),

        // Estructura del espacio (antes se ignoraban: nunca se decía si había pared, piso o escaleras)
        "wall" to NombreEs("pared", "una pared", true),
        "floor" to NombreEs("piso", "un piso", false),
        "ceiling" to NombreEs("techo", "un techo", false),
        "stairs" to NombreEs("escaleras", "unas escaleras", true),
        "room" to NombreEs("habitación", "una habitación", true),
        "picture frame" to NombreEs("marco", "un marco", false),

        // Baño
        "toilet" to NombreEs("inodoro", "un inodoro", false),
        "sink" to NombreEs("lavabo", "un lavabo", false),
        "bathtub" to NombreEs("tina", "una tina", true),
        "towel" to NombreEs("toalla", "una toalla", true),
        "soap" to NombreEs("jabón", "un jabón", false),
        "toothbrush" to NombreEs("cepillo de dientes", "un cepillo de dientes", false),

        // Electrodomésticos de cocina
        "refrigerator" to NombreEs("refrigerador", "un refrigerador", false),
        "oven" to NombreEs("horno", "un horno", false),
        "stove" to NombreEs("estufa", "una estufa", true),
        "microwave oven" to NombreEs("microondas", "un microondas", false),
        "kettle" to NombreEs("tetera", "una tetera", true),
        "toaster" to NombreEs("tostadora", "una tostadora", true),
        "blender" to NombreEs("licuadora", "una licuadora", true),
        "washing machine" to NombreEs("lavadora", "una lavadora", true),
        "dishwasher" to NombreEs("lavavajillas", "un lavavajillas", false),

        // Papelería y oficina
        "scissors" to NombreEs("tijeras", "unas tijeras", true),
        "stapler" to NombreEs("engrapadora", "una engrapadora", true),
        "calculator" to NombreEs("calculadora", "una calculadora", true),
        "ruler" to NombreEs("regla", "una regla", true),
        "eraser" to NombreEs("borrador", "un borrador", false),
        "envelope" to NombreEs("sobre", "un sobre", false),
        "whiteboard" to NombreEs("pizarrón", "un pizarrón", false),

        // Calle y exterior
        "traffic light" to NombreEs("semáforo", "un semáforo", false),
        "stop sign" to NombreEs("señal de alto", "una señal de alto", true),
        "street light" to NombreEs("poste de luz", "un poste de luz", false),
        "bench" to NombreEs("banca", "una banca", true),
        "trash can" to NombreEs("bote de basura", "un bote de basura", false),
        "fire hydrant" to NombreEs("hidrante", "un hidrante", false),
        "sidewalk" to NombreEs("banqueta", "una banqueta", true),

        // Deportes y varios
        "ball" to NombreEs("pelota", "una pelota", true),
        "football" to NombreEs("balón", "un balón", false),
        "basketball" to NombreEs("balón de básquetbol", "un balón de básquetbol", false),
        "banknote" to NombreEs("billete", "un billete", false),
        "coin" to NombreEs("moneda", "una moneda", true),
        "scale" to NombreEs("báscula", "una báscula", true),
        "candle" to NombreEs("vela", "una vela", true),
        "fan" to NombreEs("ventilador", "un ventilador", false),
        "guitar" to NombreEs("guitarra", "una guitarra", true),
        "musical instrument" to NombreEs("instrumento musical", "un instrumento musical", false),
    )

    private val etiquetasIgnoradas = setOf(
        "material", "parallel", "rectangle", "font", "pattern", "brand", "line",
        "sleeve", "wood", "metal", "plastic", "indoor", "flooring",
        "technology", "electronic device", "single", "snapshot",
        "photography", "design", "circle", "square", "symmetry", "mesh", "space",
        "component", "magenta", "cyan", "gray", "white", "black", "red", "blue",
        "green", "yellow", "orange", "pink", "purple", "brown", "angle",
        "text", "number", "symbol", "shape", "triangle", "curve", "logo",
        "still life photography", "stock photography", "picture frame style",
    )

    fun traducir(etiquetaIngles: String): NombreEs? {
        val clave = etiquetaIngles.lowercase().trim()
        return traducciones[clave]
    }

    fun esIgnorada(etiquetaIngles: String): Boolean {
        val clave = etiquetaIngles.lowercase().trim()
        return clave in etiquetasIgnoradas
    }

    fun categoriaGruesa(categoria: String?): NombreEs? {
        if (categoria == null) return null
        return when (categoria.lowercase().trim()) {
            "home goods", "home good", "household goods" -> NombreEs("objeto del hogar", "un objeto del hogar", false)
            "fashion goods", "fashion good" -> NombreEs("objeto de vestir", "un objeto de vestir", false)
            "food" -> NombreEs("alimento", "un alimento", false)
            "plant" -> NombreEs("planta", "una planta", true)
            "place" -> NombreEs("lugar", "un lugar", false)
            else -> null
        }
    }

    fun colorConcordado(color: String, nombre: NombreEs): String? {
        if (color == "desconocido") return null
        if (!nombre.esFemenino) return color
        return when (color) {
            "negro" -> "negra"
            "blanco" -> "blanca"
            "rojo" -> "roja"
            "amarillo" -> "amarilla"
            "morado" -> "morada"
            "café" -> "café"
            "rosa" -> "rosa"
            "naranja" -> "naranja"
            "azul" -> "azul"
            "verde" -> "verde"
            "gris" -> "gris"
            else -> color
        }
    }
}
