package com.example.billing

import com.example.data.ProductItem

data class HsnSuggestion(
    val hsnCode: String,
    val gstRate: Double,
    val category: String
)

data class ProductHsnPlanItem(
    val product: ProductItem,
    val currentHsn: String,
    val suggestedHsn: String,
    val suggestedGstRate: Double?,
    val category: String,
    val matched: Boolean
)

/**
 * Offline-ready Indian GST HSN lookup helper.
 * Maps product names (English + Hindi/Hinglish retail terms) to standard GST HSN codes and slabs.
 */
object HsnAutoFillHelper {

    private data class HsnRule(
        val keywords: List<String>,
        val hsnCode: String,
        val gstRate: Double,
        val category: String,
        val wholeWordOnly: Boolean = false
    )

    // Ordered from most specific accessories/items to broader product categories so e.g.
    // "Vivo Back Cover" matches Mobile Cover (3926) rather than Mobile Phone (8517).
    private val RULES = listOf(
        // 1. Mobile & Computer Accessories (check BEFORE phone brands)
        HsnRule(
            keywords = listOf("tempered glass", "tempered", "screen guard", "screen protector", "gorilla glass"),
            hsnCode = "7007",
            gstRate = 18.0,
            category = "Tempered Glass"
        ),
        HsnRule(
            keywords = listOf("back cover", "mobile cover", "phone cover", "flip cover", "silicone cover", "phone case", "mobile pouch"),
            hsnCode = "3926",
            gstRate = 18.0,
            category = "Mobile Cover / Case"
        ),
        HsnRule(
            keywords = listOf("cover", "case", "pouch"),
            hsnCode = "3926",
            gstRate = 18.0,
            category = "Cover / Case",
            wholeWordOnly = true
        ),
        HsnRule(
            keywords = listOf("laptop bag", "camera bag", "school bag", "travel bag", "trolley bag", "backpack"),
            hsnCode = "4202",
            gstRate = 18.0,
            category = "Bags & Luggage"
        ),
        HsnRule(
            keywords = listOf("charger", "adapter", "adaptor", "power bank", "powerbank", "inverter", "ups", "stabilizer"),
            hsnCode = "8504",
            gstRate = 18.0,
            category = "Charger / Power"
        ),
        HsnRule(
            keywords = listOf("earphone", "headphone", "earbud", "earpods", "airpod", "airpods", "neckband", "tws", "bluetooth speaker", "speaker", "soundbar", "microphone", "woofter", "home theatre"),
            hsnCode = "8518",
            gstRate = 18.0,
            category = "Audio / Earphones"
        ),
        HsnRule(
            keywords = listOf("mic"),
            hsnCode = "8518",
            gstRate = 18.0,
            category = "Microphone",
            wholeWordOnly = true
        ),
        HsnRule(
            keywords = listOf("pendrive", "pen drive", "memory card", "sd card", "micro sd", "microsd", "hard disk", "ssd", "hdd", "flash drive"),
            hsnCode = "8523",
            gstRate = 18.0,
            category = "Storage / Memory"
        ),
        HsnRule(
            keywords = listOf("data cable", "usb cable", "type-c cable", "type c cable", "lightning cable", "charging cable", "otg", "hdmi", "electric wire", "wire", "cable"),
            hsnCode = "8544",
            gstRate = 18.0,
            category = "Cables & Wires"
        ),
        HsnRule(
            keywords = listOf("mobile battery", "phone battery", "lithium battery", "inverter battery", "battery"),
            hsnCode = "8507",
            gstRate = 18.0,
            category = "Battery"
        ),
        HsnRule(
            keywords = listOf("smartwatch", "smart watch", "fitness band"),
            hsnCode = "8517",
            gstRate = 18.0,
            category = "Smartwatch"
        ),

        // 2. Mobile Phones & Telecom
        HsnRule(
            keywords = listOf(
                "mobile", "smartphone", "handset", "iphone", "samsung", "galaxy",
                "redmi", "xiaomi", "realme", "vivo", "oppo", "oneplus", "poco",
                "motorola", "nokia", "iqoo", "nothing", "pixel", "tecno", "infinix",
                "lava", "micromax", "itel", "honor", "jio phone", "keypad phone",
                "router", "modem", "dongle"
            ),
            hsnCode = "8517",
            gstRate = 18.0,
            category = "Mobile / Telecom"
        ),
        HsnRule(
            keywords = listOf("phone", "moto", "5g", "4g", "wifi"),
            hsnCode = "8517",
            gstRate = 18.0,
            category = "Mobile / Telecom",
            wholeWordOnly = true
        ),

        // 3. Computers, Printers & CCTV
        HsnRule(
            keywords = listOf("laptop", "computer", "desktop", "tablet", "ipad", "macbook", "keyboard", "mouse", "monitor", "motherboard", "processor", "ram"),
            hsnCode = "8471",
            gstRate = 18.0,
            category = "Computer / IT"
        ),
        HsnRule(
            keywords = listOf("pc"),
            hsnCode = "8471",
            gstRate = 18.0,
            category = "Computer / IT",
            wholeWordOnly = true
        ),
        HsnRule(
            keywords = listOf("printer", "scanner", "cartridge", "toner", "xerox"),
            hsnCode = "8443",
            gstRate = 18.0,
            category = "Printer / Scanner"
        ),
        HsnRule(
            keywords = listOf("cctv", "camera", "dslr", "dvr", "nvr", "webcam"),
            hsnCode = "8525",
            gstRate = 18.0,
            category = "Camera / CCTV"
        ),

        // 4. Electronics & Appliances
        HsnRule(
            keywords = listOf("television", "led tv", "smart tv", "android tv", "qled", "projector"),
            hsnCode = "8528",
            gstRate = 18.0,
            category = "TV / Display"
        ),
        HsnRule(
            keywords = listOf("tv"),
            hsnCode = "8528",
            gstRate = 18.0,
            category = "TV / Display",
            wholeWordOnly = true
        ),
        HsnRule(
            keywords = listOf("refrigerator", "fridge", "freezer", "deep freezer"),
            hsnCode = "8418",
            gstRate = 18.0,
            category = "Refrigerator"
        ),
        HsnRule(
            keywords = listOf("air conditioner", "split ac", "window ac", "inverter ac"),
            hsnCode = "8415",
            gstRate = 28.0,
            category = "Air Conditioner"
        ),
        HsnRule(
            keywords = listOf("ac"),
            hsnCode = "8415",
            gstRate = 28.0,
            category = "Air Conditioner",
            wholeWordOnly = true
        ),
        HsnRule(
            keywords = listOf("air cooler", "desert cooler", "cooler"),
            hsnCode = "8479",
            gstRate = 18.0,
            category = "Air Cooler"
        ),
        HsnRule(
            keywords = listOf("washing machine", "washer", "dryer"),
            hsnCode = "8450",
            gstRate = 18.0,
            category = "Washing Machine"
        ),
        HsnRule(
            keywords = listOf("ceiling fan", "table fan", "pedestal fan", "exhaust fan", "wall fan", "cooler fan", "pump", "water pump", "submersible"),
            hsnCode = "8414",
            gstRate = 18.0,
            category = "Fans / Pumps"
        ),
        HsnRule(
            keywords = listOf("fan"),
            hsnCode = "8414",
            gstRate = 18.0,
            category = "Fan",
            wholeWordOnly = true
        ),
        HsnRule(
            keywords = listOf("geyser", "water heater", "room heater", "heater", "induction", "electric iron", "steam iron", "kettle", "toaster", "sandwich", "microwave", "oven", "mixer", "grinder", "juicer", "blender"),
            hsnCode = "8516",
            gstRate = 18.0,
            category = "Kitchen / Heating Appliance"
        ),
        HsnRule(
            keywords = listOf("iron", "press"),
            hsnCode = "8516",
            gstRate = 18.0,
            category = "Electric Iron",
            wholeWordOnly = true
        ),
        HsnRule(
            keywords = listOf("led bulb", "tube light", "tubelight", "bulb", "cfl", "halogen", "spotlight", "panel light", "street light", "flood light"),
            hsnCode = "8539",
            gstRate = 18.0,
            category = "Lighting / Bulbs"
        ),
        HsnRule(
            keywords = listOf("torch", "emergency light", "jhalar", "fairy light", "chandelier", "lamp"),
            hsnCode = "9405",
            gstRate = 18.0,
            category = "Lamps & Torch"
        ),
        HsnRule(
            keywords = listOf("switch", "socket", "regulator", "mcb", "distribution box", "plug", "extension board", "holder"),
            hsnCode = "8536",
            gstRate = 18.0,
            category = "Electrical Switches"
        ),
        HsnRule(
            keywords = listOf("wrist watch", "wall clock", "clock", "ghadi"),
            hsnCode = "9102",
            gstRate = 18.0,
            category = "Watches / Clocks"
        ),

        // 5. Kirana / Grocery / Food / Dairy
        HsnRule(
            keywords = listOf("basmati", "rice", "chawal", "poha", "murmura", "idli rice", "sona masoori"),
            hsnCode = "1006",
            gstRate = 5.0,
            category = "Rice / Grain"
        ),
        HsnRule(
            keywords = listOf("atta", "wheat flour", "wheat", "maida", "suji", "sooji", "rava", "besan", "daliya", "gehun", "flour"),
            hsnCode = "1101",
            gstRate = 5.0,
            category = "Atta / Flour"
        ),
        HsnRule(
            keywords = listOf("toor dal", "arhar", "moong", "masoor", "urad", "chana dal", "rajma", "kabuli chana", "lobia", "pulses", "lentil", "chana"),
            hsnCode = "0713",
            gstRate = 5.0,
            category = "Dal / Pulses"
        ),
        HsnRule(
            keywords = listOf("dal"),
            hsnCode = "0713",
            gstRate = 5.0,
            category = "Dal / Pulses",
            wholeWordOnly = true
        ),
        HsnRule(
            keywords = listOf("sugar", "cheeni", "shakkar", "jaggery", "mishri", "misri", "brown sugar"),
            hsnCode = "1701",
            gstRate = 5.0,
            category = "Sugar / Jaggery"
        ),
        HsnRule(
            keywords = listOf("gud"),
            hsnCode = "1701",
            gstRate = 5.0,
            category = "Jaggery",
            wholeWordOnly = true
        ),
        HsnRule(
            keywords = listOf("mustard oil", "sarson oil", "sarson tel", "kachi ghani", "mustard"),
            hsnCode = "1514",
            gstRate = 5.0,
            category = "Mustard Oil"
        ),
        HsnRule(
            keywords = listOf("sunflower oil", "refined oil", "soybean oil", "soya oil", "groundnut oil", "rice bran", "cooking oil", "fortune oil", "palmolein"),
            hsnCode = "1512",
            gstRate = 5.0,
            category = "Refined / Cooking Oil"
        ),
        HsnRule(
            keywords = listOf("hair oil", "amla oil", "coconut hair oil", "jasmine oil", "shampoo", "conditioner"),
            hsnCode = "3305",
            gstRate = 18.0,
            category = "Hair Care"
        ),
        HsnRule(
            keywords = listOf("engine oil", "lubricant", "grease", "castrol", "servoil", "gear oil", "diesel", "petrol"),
            hsnCode = "2710",
            gstRate = 18.0,
            category = "Lubricants / Engine Oil"
        ),
        HsnRule(
            keywords = listOf("mobil"),
            hsnCode = "2710",
            gstRate = 18.0,
            category = "Engine Oil",
            wholeWordOnly = true
        ),
        HsnRule(
            keywords = listOf("oil", "tel"),
            hsnCode = "1515",
            gstRate = 5.0,
            category = "Edible Oil",
            wholeWordOnly = true
        ),
        HsnRule(
            keywords = listOf("ghee", "desi ghee", "butter", "makkhan", "amul butter"),
            hsnCode = "0405",
            gstRate = 12.0,
            category = "Ghee / Butter"
        ),
        HsnRule(
            keywords = listOf("paneer", "cheese", "mozzarella"),
            hsnCode = "0406",
            gstRate = 12.0,
            category = "Paneer / Cheese"
        ),
        HsnRule(
            keywords = listOf("milk", "doodh", "curd", "dahi", "lassi", "buttermilk", "chach"),
            hsnCode = "0401",
            gstRate = 0.0,
            category = "Milk / Dairy"
        ),
        HsnRule(
            keywords = listOf("honey", "shahad", "dabur honey"),
            hsnCode = "0409",
            gstRate = 5.0,
            category = "Honey"
        ),
        HsnRule(
            keywords = listOf("tea", "chai", "green tea", "tata tea", "red label", "taj mahal"),
            hsnCode = "0902",
            gstRate = 5.0,
            category = "Tea"
        ),
        HsnRule(
            keywords = listOf("coffee", "nescafe", "bru"),
            hsnCode = "0901",
            gstRate = 18.0,
            category = "Coffee"
        ),
        HsnRule(
            keywords = listOf("masala", "spice", "haldi", "turmeric", "mirch", "chilli powder", "dhaniya", "coriander", "jeera", "cumin", "kali mirch", "elaichi", "laung", "hing"),
            hsnCode = "0910",
            gstRate = 5.0,
            category = "Spices / Masala"
        ),
        HsnRule(
            keywords = listOf("salt", "namak", "tata salt", "sendha"),
            hsnCode = "2501",
            gstRate = 0.0,
            category = "Salt"
        ),
        HsnRule(
            keywords = listOf("biscuit", "cookies", "cookie", "parle", "britannia", "oreo", "rusk", "toast", "cake", "pastry", "bread"),
            hsnCode = "1905",
            gstRate = 18.0,
            category = "Biscuits & Bakery"
        ),
        HsnRule(
            keywords = listOf("maggi", "noodle", "noodles", "pasta", "macaroni", "vermicelli", "sewai", "chowmein"),
            hsnCode = "1902",
            gstRate = 12.0,
            category = "Noodles / Pasta"
        ),
        HsnRule(
            keywords = listOf("chocolate", "dairy milk", "kitkat", "five star", "munch", "perk", "cocoa"),
            hsnCode = "1806",
            gstRate = 18.0,
            category = "Chocolate"
        ),
        HsnRule(
            keywords = listOf("toffee", "candy", "eclairs", "lollipop", "chewing gum"),
            hsnCode = "1704",
            gstRate = 18.0,
            category = "Confectionery"
        ),
        HsnRule(
            keywords = listOf("namkeen", "bhujia", "chips", "kurkure", "mixture", "sev", "papad", "laddu", "barfi", "mithai", "gulab jamun", "rasgulla"),
            hsnCode = "2106",
            gstRate = 12.0,
            category = "Snacks / Namkeen"
        ),
        HsnRule(
            keywords = listOf("ice cream", "icecream", "kulfi"),
            hsnCode = "2105",
            gstRate = 18.0,
            category = "Ice Cream"
        ),
        HsnRule(
            keywords = listOf("sauce", "ketchup", "pickle", "achar", "chutney", "jam"),
            hsnCode = "2103",
            gstRate = 12.0,
            category = "Sauce / Pickle"
        ),
        HsnRule(
            keywords = listOf("kaju", "cashew", "badam", "almond", "pista", "pistachio", "kishmish", "raisin", "akhrot", "walnut", "makhana", "dry fruit", "date", "khajoor"),
            hsnCode = "0802",
            gstRate = 12.0,
            category = "Dry Fruits"
        ),
        HsnRule(
            keywords = listOf("mineral water", "drinking water", "water bottle", "bisleri", "aquafina", "kinley"),
            hsnCode = "2201",
            gstRate = 18.0,
            category = "Packaged Water"
        ),
        HsnRule(
            keywords = listOf("cold drink", "soft drink", "coca cola", "coke", "pepsi", "sprite", "fanta", "thums up", "limca", "mountain dew", "maaza", "frooti", "juice", "sting", "red bull", "soda"),
            hsnCode = "2202",
            gstRate = 28.0,
            category = "Beverages / Cold Drink"
        ),

        // 6. Personal Care, Soaps & Detergents
        HsnRule(
            keywords = listOf("bath soap", "soap", "sabun", "lux", "lifebuoy", "dettol", "santoor", "dove", "pears", "handwash", "body wash"),
            hsnCode = "3401",
            gstRate = 18.0,
            category = "Soaps"
        ),
        HsnRule(
            keywords = listOf("detergent", "washing powder", "surf", "tide", "ariel", "wheel", "rin", "ghadi", "vim", "dishwash", "harpic", "phenyl", "lizol", "floor cleaner"),
            hsnCode = "3402",
            gstRate = 18.0,
            category = "Detergent & Cleaners"
        ),
        HsnRule(
            keywords = listOf("toothpaste", "colgate", "pepsodent", "sensodyne", "closeup", "dant kanti", "mouthwash"),
            hsnCode = "3306",
            gstRate = 18.0,
            category = "Oral Care"
        ),
        HsnRule(
            keywords = listOf("face cream", "moisturizer", "lotion", "facewash", "face wash", "talcum", "powder", "lipstick", "kajal", "perfume", "deodorant", "deo", "fogg", "sunscreen", "cream"),
            hsnCode = "3304",
            gstRate = 18.0,
            category = "Cosmetics / Skincare"
        ),
        HsnRule(
            keywords = listOf("agarbatti", "incense", "dhoop"),
            hsnCode = "3307",
            gstRate = 5.0,
            category = "Agarbatti / Dhoop"
        ),
        HsnRule(
            keywords = listOf("mosquito", "good knight", "all out", "mortien", "hit spray", "pesticide", "insecticide", "herbicide"),
            hsnCode = "3808",
            gstRate = 18.0,
            category = "Pest Control / Repellent"
        ),

        // 7. Medical / Pharmacy
        HsnRule(
            keywords = listOf("tablet", "capsule", "syrup", "medicine", "dawai", "ointment", "eye drop", "ear drop", "injection", "paracetamol", "antibiotic", "bandage", "cotton", "sanitizer", "ors"),
            hsnCode = "3004",
            gstRate = 12.0,
            category = "Medicine / Pharmacy"
        ),
        HsnRule(
            keywords = listOf("mask", "gloves", "thermometer", "bp monitor", "oximeter", "glucometer", "nebulizer"),
            hsnCode = "9018",
            gstRate = 12.0,
            category = "Medical Device"
        ),

        // 8. Hardware, Sanitary, Paint & Construction
        HsnRule(
            keywords = listOf("cement", "ultratech", "ambuja", "acc cement", "bangur", "birla", "white cement"),
            hsnCode = "2523",
            gstRate = 28.0,
            category = "Cement"
        ),
        HsnRule(
            keywords = listOf("paint", "primer", "putty", "wall putty", "enamel", "emulsion", "distemper", "varnish", "asian paint", "berger", "nerolac"),
            hsnCode = "3209",
            gstRate = 18.0,
            category = "Paints & Putty"
        ),
        HsnRule(
            keywords = listOf("pvc pipe", "upvc", "cpvc", "water pipe", "hdpe", "conduit", "elbow", "socket pipe", "water tank", "sintex"),
            hsnCode = "3917",
            gstRate = 18.0,
            category = "PVC Pipes & Fittings"
        ),
        HsnRule(
            keywords = listOf("pipe", "tank"),
            hsnCode = "3917",
            gstRate = 18.0,
            category = "Pipes / Tank",
            wholeWordOnly = true
        ),
        HsnRule(
            keywords = listOf("tap", "bibcock", "angle cock", "mixer tap", "shower", "valve", "faucet"),
            hsnCode = "8481",
            gstRate = 18.0,
            category = "Taps & Valves"
        ),
        HsnRule(
            keywords = listOf("tile", "tiles", "ceramic", "granite", "marble", "wash basin", "commode", "toilet seat"),
            hsnCode = "6907",
            gstRate = 18.0,
            category = "Tiles / Sanitaryware"
        ),
        HsnRule(
            keywords = listOf("sariya", "tmt bar", "tmt", "iron bar", "steel rod", "angle iron", "ms channel"),
            hsnCode = "7214",
            gstRate = 18.0,
            category = "Iron & Steel Bars"
        ),
        HsnRule(
            keywords = listOf("screw", "bolt", "nut", "washer", "nail", "fastener"),
            hsnCode = "7318",
            gstRate = 18.0,
            category = "Nuts, Bolts & Screws"
        ),
        HsnRule(
            keywords = listOf("padlock", "lock", "tala", "hinge", "kabja", "aldrop", "handle", "latch", "door closer"),
            hsnCode = "8301",
            gstRate = 18.0,
            category = "Locks & Door Hardware"
        ),
        HsnRule(
            keywords = listOf("plywood", "ply", "blockboard", "flush door", "sunmica", "laminate", "veneer"),
            hsnCode = "4412",
            gstRate = 18.0,
            category = "Plywood & Laminates"
        ),
        HsnRule(
            keywords = listOf("hammer", "plier", "wrench", "spanner", "screwdriver", "drill bit", "hacksaw", "cutter", "tool"),
            hsnCode = "8205",
            gstRate = 18.0,
            category = "Hand Tools"
        ),

        // 9. Garments, Textiles & Footwear
        HsnRule(
            keywords = listOf("t-shirt", "tshirt", "t shirt", "polo", "sweater", "cardigan", "hoodie", "vest", "baniyan"),
            hsnCode = "6109",
            gstRate = 5.0,
            category = "Knitted Apparel"
        ),
        HsnRule(
            keywords = listOf("shirt", "formal shirt", "casual shirt", "kurta"),
            hsnCode = "6205",
            gstRate = 5.0,
            category = "Shirts / Kurtas"
        ),
        HsnRule(
            keywords = listOf("jeans", "trouser", "pant", "pants", "lower", "track pant", "jacket", "blazer", "suit"),
            hsnCode = "6203",
            gstRate = 12.0,
            category = "Trousers / Jeans"
        ),
        HsnRule(
            keywords = listOf("kurti", "salwar", "lehenga", "dupatta", "frock", "gown", "leggings"),
            hsnCode = "6204",
            gstRate = 5.0,
            category = "Ladies Wear"
        ),
        HsnRule(
            keywords = listOf("saree", "sari", "banarasi", "silk saree"),
            hsnCode = "5407",
            gstRate = 5.0,
            category = "Saree / Fabric"
        ),
        HsnRule(
            keywords = listOf("bedsheet", "bed sheet", "chadar", "towel", "gamcha", "pillow cover", "curtain", "parda"),
            hsnCode = "6302",
            gstRate = 5.0,
            category = "Home Textile"
        ),
        HsnRule(
            keywords = listOf("blanket", "kambal", "quilt", "razai"),
            hsnCode = "6301",
            gstRate = 5.0,
            category = "Blankets"
        ),
        HsnRule(
            keywords = listOf("shoes", "shoe", "sneaker", "boot", "sandal", "slipper", "chappal", "crocs", "footwear", "joota"),
            hsnCode = "6403",
            gstRate = 12.0,
            category = "Footwear"
        ),
        HsnRule(
            keywords = listOf("backpack", "school bag", "travel bag", "handbag", "purse", "wallet", "suitcase", "trolley", "luggage"),
            hsnCode = "4202",
            gstRate = 18.0,
            category = "Bags & Luggage"
        ),
        HsnRule(
            keywords = listOf("bag"),
            hsnCode = "4202",
            gstRate = 18.0,
            category = "Bags",
            wholeWordOnly = true
        ),

        // 10. Stationery, Books & Plastics
        HsnRule(
            keywords = listOf("notebook", "register", "diary", "exercise book", "copy"),
            hsnCode = "4820",
            gstRate = 12.0,
            category = "Notebooks & Registers"
        ),
        HsnRule(
            keywords = listOf("textbook", "story book", "book", "kitab", "novel"),
            hsnCode = "4901",
            gstRate = 0.0,
            category = "Printed Books"
        ),
        HsnRule(
            keywords = listOf("ball pen", "gel pen", "fountain pen", "marker", "highlighter"),
            hsnCode = "9608",
            gstRate = 18.0,
            category = "Pens & Markers"
        ),
        HsnRule(
            keywords = listOf("pen"),
            hsnCode = "9608",
            gstRate = 18.0,
            category = "Pen",
            wholeWordOnly = true
        ),
        HsnRule(
            keywords = listOf("pencil", "crayon", "eraser", "sharpener", "geometry box"),
            hsnCode = "9609",
            gstRate = 12.0,
            category = "Pencils / Drawing"
        ),
        HsnRule(
            keywords = listOf("a4 paper", "rim", "photocopy paper", "bond paper", "chart paper"),
            hsnCode = "4802",
            gstRate = 12.0,
            category = "Paper"
        ),
        HsnRule(
            keywords = listOf("cello tape", "packing tape", "bopp tape", "adhesive tape", "fevicol", "glue"),
            hsnCode = "3919",
            gstRate = 18.0,
            category = "Tapes & Adhesives"
        ),
        HsnRule(
            keywords = listOf("bucket", "balti", "plastic tub", "plastic chair", "plastic stool", "lunch box", "tiffin", "water bottle plastic", "mug", "dustbin", "basket"),
            hsnCode = "3924",
            gstRate = 18.0,
            category = "Plastic Household"
        ),

        // 11. Agriculture & Auto Parts
        HsnRule(
            keywords = listOf("urea", "dap", "npk", "potash", "fertilizer", "khad"),
            hsnCode = "3105",
            gstRate = 5.0,
            category = "Fertilizer"
        ),
        HsnRule(
            keywords = listOf("vegetable seed", "paddy seed", "wheat seed", "hybrid seed", "seed", "beej"),
            hsnCode = "1209",
            gstRate = 0.0,
            category = "Seeds"
        ),
        HsnRule(
            keywords = listOf("tyre", "tire", "mrf", "ceat", "apollo tyre"),
            hsnCode = "4011",
            gstRate = 28.0,
            category = "Tyres"
        ),
        HsnRule(
            keywords = listOf("helmet"),
            hsnCode = "6506",
            gstRate = 18.0,
            category = "Helmet"
        ),
        HsnRule(
            keywords = listOf("clutch plate", "brake pad", "brake shoe", "spark plug", "chain kit", "spare part"),
            hsnCode = "8708",
            gstRate = 28.0,
            category = "Auto Spare Parts"
        )
    )

    private val WORD_REGEX = Regex("[a-z0-9]+")

    fun suggestHsn(productName: String, currentHsn: String = ""): HsnSuggestion? {
        val cleanName = productName.trim().lowercase()
        if (cleanName.isBlank()) {
            val cleanExisting = currentHsn.trim().uppercase()
            return if (cleanExisting.isNotBlank()) {
                HsnSuggestion(cleanExisting, 18.0, "Existing HSN")
            } else null
        }

        val tokens = WORD_REGEX.findAll(cleanName).map { it.value }.toSet()
        for (rule in RULES) {
            val matched = if (rule.wholeWordOnly) {
                rule.keywords.any { kw -> kw in tokens }
            } else {
                rule.keywords.any { kw ->
                    if (kw.contains(' ') || kw.contains('-')) {
                        cleanName.contains(kw)
                    } else {
                        kw in tokens || cleanName.contains(kw)
                    }
                }
            }
            if (matched) {
                return HsnSuggestion(
                    hsnCode = rule.hsnCode,
                    gstRate = rule.gstRate,
                    category = rule.category
                )
            }
        }
        return null
    }

    /**
     * Builds a preview/execution plan for filling HSN codes across all products.
     * - When [overwriteExisting] is false, products that already have a non-blank HSN keep it.
     * - When [applyDefaultIfUnmatched] is true, unmatched items receive "9997" (General Goods/Services).
     */
    fun buildBatchPlan(
        products: List<ProductItem>,
        overwriteExisting: Boolean = false,
        alsoUpdateGstRate: Boolean = false,
        applyDefaultIfUnmatched: Boolean = false
    ): List<ProductHsnPlanItem> {
        return products.mapNotNull { prod ->
            val current = prod.hsnCode.trim().uppercase()
            if (!overwriteExisting && current.isNotBlank()) {
                return@mapNotNull null
            }
            val suggestion = suggestHsn(prod.name, current)
            when {
                suggestion != null -> ProductHsnPlanItem(
                    product = prod,
                    currentHsn = current,
                    suggestedHsn = suggestion.hsnCode,
                    suggestedGstRate = if (alsoUpdateGstRate) suggestion.gstRate else null,
                    category = suggestion.category,
                    matched = true
                )
                applyDefaultIfUnmatched -> ProductHsnPlanItem(
                    product = prod,
                    currentHsn = current,
                    suggestedHsn = "9997",
                    suggestedGstRate = null,
                    category = "General Retail (Default)",
                    matched = false
                )
                else -> ProductHsnPlanItem(
                    product = prod,
                    currentHsn = current,
                    suggestedHsn = current,
                    suggestedGstRate = null,
                    category = "Unmatched (Enter manually)",
                    matched = false
                )
            }
        }
    }
}
