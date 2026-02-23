# Material 3 Expressive — Guida Completa al Design System Android 16 (2026)

> Material 3 Expressive è l'equivalente di Liquid Glass su iOS 26. È il linguaggio visivo di Android 16, con animazioni spring-based, componenti espressivi, colori dinamici e tipografia impattante. Questo documento contiene tutti i componenti M3 Expressive e i relativi codici Compose.

---

## 1. COS'È MATERIAL 3 EXPRESSIVE

Material 3 Expressive (M3 Expressive) è l'evoluzione di Material You (Material 3), introdotto con Android 16. NON è Material 4 — è un'estensione espressiva di M3 con:

- **Animazioni spring-based**: interazioni fluide e naturali con physics-based motion
- **Componenti rinnovati**: 15+ nuovi componenti UI o refresh di esistenti
- **Colori più vibranti**: palette tonali più profonde e token set più ampio
- **Tipografia enfatizzata**: variable font axes per più espressione
- **Forme espressive**: shape morphing, corner radius come linguaggio espressivo
- **Dynamic Color**: colori derivati dallo sfondo dell'utente (Material You)

### Dipendenze necessarie
```kotlin
// Le API M3 Expressive sono nella libreria material3 standard
// Molte sono ancora @ExperimentalMaterial3ExpressiveApi
implementation("androidx.compose.material3:material3:1.4.0")  // o versione più recente
```

### Opt-in per API sperimentali
```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MioComponente() {
    // Usa componenti M3 Expressive qui
}
```

---

## 2. MOTION SCHEME — ANIMAZIONI SPRING-BASED

M3 Expressive introduce un `MotionScheme` che definisce le animazioni per tutti i componenti.

```kotlin
// Il MotionScheme è impostato tramite MaterialTheme
MaterialTheme(
    // Il MotionScheme è parte del tema — i componenti lo usano automaticamente
) {
    // Tutti i componenti M3 dentro al tema usano le animazioni spring-based
}

// Animazioni spring personalizzate (coerenti con M3 Expressive)
val animSpec = spring<Float>(
    dampingRatio = Spring.DampingRatioMediumBouncy,  // Bouncy come M3 Expressive
    stiffness = Spring.StiffnessMedium
)

// Livelli di bounciness consigliati per M3 Expressive:
// Spring.DampingRatioLowBouncy     = 0.75f  (molto bouncy)
// Spring.DampingRatioMediumBouncy  = 0.5f   (bouncy medio — CONSIGLIATO per M3E)
// Spring.DampingRatioHighBouncy    = 0.2f   (estremamente bouncy)
// Spring.DampingRatioNoBouncy      = 1.0f   (nessun bounce)

// Esempio animazione bouncy stile M3 Expressive
@Composable
fun BottoneAnimato() {
    var premuto by remember { mutableStateOf(false) }

    val scala by animateFloatAsState(
        targetValue = if (premuto) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "scala"
    )

    Button(
        onClick = { /* azione */ },
        modifier = Modifier
            .scale(scala)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        premuto = true
                        tryAwaitRelease()
                        premuto = false
                    }
                )
            }
    ) {
        Text("Premi")
    }
}
```

---

## 3. NUOVI COMPONENTI M3 EXPRESSIVE

### 3.1 LoadingIndicator — Indicatore di caricamento espressivo
```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LoadingIndicatorExamples() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Loading indicator base (animazione con forme poligonali)
        LoadingIndicator()

        // Con solo 2 forme
        LoadingIndicator(
            polygons = LoadingIndicatorDefaults.IndeterminateIndicatorPolygons.take(2)
        )

        // Contained (dentro un container con sfondo)
        ContainedLoadingIndicator()

        // Con colore container personalizzato
        ContainedLoadingIndicator(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    }
}
```

### 3.2 SplitButton — Bottone diviso con azione primaria e secondaria
```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SplitButtonExample() {
    var expanded by remember { mutableStateOf(false) }

    SplitButtonLayout(
        leadingButton = {
            SplitButtonDefaults.LeadingButton(
                onClick = { /* Azione primaria */ }
            ) {
                Icon(
                    Icons.Filled.Edit,
                    modifier = Modifier.size(SplitButtonDefaults.LeadingIconSize),
                    contentDescription = null
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text("Modifica")
            }
        },
        trailingButton = {
            SplitButtonDefaults.TrailingButton(
                checked = expanded,
                onCheckedChange = { expanded = it }
            ) {
                Icon(
                    Icons.Filled.ArrowDropDown,
                    modifier = Modifier.size(SplitButtonDefaults.TrailingIconSize),
                    contentDescription = "Opzioni"
                )
            }
        }
    )

    // Dropdown che appare quando si espande
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false }
    ) {
        DropdownMenuItem(
            text = { Text("Rinomina") },
            onClick = { expanded = false }
        )
        DropdownMenuItem(
            text = { Text("Duplica") },
            onClick = { expanded = false }
        )
    }
}

// Filled SplitButton variant
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FilledSplitButtonExample() {
    var checked by remember { mutableStateOf(false) }

    SplitButtonLayout(
        leadingButton = {
            SplitButtonDefaults.LeadingButton(
                onClick = { /* azione */ }
            ) {
                Icon(
                    Icons.Filled.Star,
                    modifier = Modifier.size(SplitButtonDefaults.LeadingIconSize),
                    contentDescription = null
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text("Preferito")
            }
        },
        trailingButton = {
            SplitButtonDefaults.TrailingButton(
                checked = checked,
                onCheckedChange = { checked = it }
            ) {
                Icon(
                    Icons.Filled.ArrowDropDown,
                    modifier = Modifier.size(SplitButtonDefaults.TrailingIconSize),
                    contentDescription = null
                )
            }
        }
    )
}
```

### 3.3 ButtonGroup — Gruppo di bottoni espressivi
```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ButtonGroupExample() {
    // ButtonGroup con animazione di pressione
    // Quando premi un bottone, si espande e i vicini si comprimono
    ButtonGroup {
        Button(onClick = { /* azione 1 */ }) {
            Text("Opzione A")
        }
        Button(onClick = { /* azione 2 */ }) {
            Text("Opzione B")
        }
        Button(onClick = { /* azione 3 */ }) {
            Text("Opzione C")
        }
    }
}

// Connected ButtonGroup — Bottoni connessi per selezione singola
// (Sostituisce il vecchio SegmentedButton)
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ConnectedButtonGroupExample() {
    val opzioni = listOf("Lavoro", "Ristorante", "Caffè")
    val iconeDeselezionate = listOf(
        Icons.Outlined.Work,
        Icons.Outlined.Restaurant,
        Icons.Outlined.Coffee
    )
    val iconeSelezionate = listOf(
        Icons.Filled.Work,
        Icons.Filled.Restaurant,
        Icons.Filled.Coffee
    )
    var indiceScelto by remember { mutableIntStateOf(0) }

    Row(
        modifier = Modifier.padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(
            ButtonGroupDefaults.ConnectedSpaceBetween
        )
    ) {
        opzioni.forEachIndexed { indice, etichetta ->
            ToggleButton(
                checked = indiceScelto == indice,
                onCheckedChange = { indiceScelto = indice }
            ) {
                Icon(
                    if (indiceScelto == indice) iconeSelezionate[indice]
                    else iconeDeselezionate[indice],
                    contentDescription = null
                )
                Spacer(Modifier.size(ToggleButtonDefaults.IconSpacing))
                Text(etichetta)
            }
        }
    }
}
```

### 3.4 FloatingToolbar — Toolbar flottante (orizzontale e verticale)
```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HorizontalFloatingToolbarExample() {
    var expanded by rememberSaveable { mutableStateOf(true) }
    val vibrantColors = FloatingToolbarDefaults.vibrantFloatingToolbarColors()

    Scaffold { innerPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Contenuto scrollabile
            LazyColumn(
                modifier = Modifier.floatingToolbarVerticalNestedScroll(
                    expanded = expanded,
                    onExpand = { expanded = true },
                    onCollapse = { expanded = false }
                )
            ) {
                items(50) { index ->
                    Text(
                        "Elemento $index",
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            // Floating toolbar con FAB
            HorizontalFloatingToolbar(
                expanded = expanded,
                floatingActionButton = {
                    FloatingToolbarDefaults.VibrantFloatingActionButton(
                        onClick = { /* aggiungi */ }
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Aggiungi")
                    }
                },
                modifier = Modifier.align(Alignment.BottomEnd),
                colors = vibrantColors,
                content = {
                    IconButton(onClick = { /* persona */ }) {
                        Icon(Icons.Filled.Person, contentDescription = "Profilo")
                    }
                    IconButton(onClick = { /* modifica */ }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Modifica")
                    }
                    IconButton(onClick = { /* condividi */ }) {
                        Icon(Icons.Filled.Share, contentDescription = "Condividi")
                    }
                }
            )
        }
    }
}

// Vertical Floating Toolbar
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VerticalFloatingToolbarExample() {
    var expanded by rememberSaveable { mutableStateOf(true) }

    Scaffold { innerPadding ->
        Box(
            Modifier.fillMaxSize().padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier.floatingToolbarVerticalNestedScroll(
                    expanded = expanded,
                    onExpand = { expanded = true },
                    onCollapse = { expanded = false }
                )
            ) {
                items(50) { Text("Elemento $it", Modifier.padding(16.dp)) }
            }

            VerticalFloatingToolbar(
                modifier = Modifier.align(Alignment.CenterEnd),
                expanded = expanded,
                leadingContent = {
                    IconButton(onClick = {}) {
                        Icon(Icons.Filled.MoreVert, null)
                    }
                },
                trailingContent = {
                    IconButton(onClick = {}) {
                        Icon(Icons.Filled.Star, null)
                    }
                },
                content = {
                    IconButton(onClick = {}) { Icon(Icons.Filled.Edit, null) }
                    IconButton(onClick = {}) { Icon(Icons.Filled.Share, null) }
                    IconButton(onClick = {}) { Icon(Icons.Filled.Delete, null) }
                }
            )
        }
    }
}
```

### 3.5 FloatingActionButtonMenu — Menu FAB espressivo
```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FABMenuExample() {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButtonMenu(
                expanded = expanded,
                button = {
                    ToggleFloatingActionButton(
                        checked = expanded,
                        onCheckedChange = { expanded = it }
                    ) {
                        val rotation by animateFloatAsState(
                            targetValue = if (expanded) 135f else 0f,
                            label = "fab_rotation"
                        )
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Menu",
                            modifier = Modifier.rotate(rotation)
                        )
                    }
                }
            ) {
                // Menu items
                FloatingActionButtonMenuItem(
                    onClick = {
                        expanded = false
                        /* azione foto */
                    },
                    icon = { Icon(Icons.Default.CameraAlt, null) },
                    text = { Text("Scatta foto") }
                )
                FloatingActionButtonMenuItem(
                    onClick = {
                        expanded = false
                        /* azione galleria */
                    },
                    icon = { Icon(Icons.Default.PhotoLibrary, null) },
                    text = { Text("Dalla galleria") }
                )
                FloatingActionButtonMenuItem(
                    onClick = {
                        expanded = false
                        /* azione file */
                    },
                    icon = { Icon(Icons.Default.AttachFile, null) },
                    text = { Text("Allega file") }
                )
            }
        }
    ) { paddingValues ->
        // Contenuto
        Box(Modifier.padding(paddingValues)) { /* ... */ }
    }
}
```

### 3.6 FlexibleBottomAppBar — Bottom bar adattiva
```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FlexibleBottomAppBarExample() {
    Scaffold(
        bottomBar = {
            FlexibleBottomAppBar(
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                IconButton(onClick = {}) {
                    Icon(Icons.Filled.Home, contentDescription = "Home")
                }
                IconButton(onClick = {}) {
                    Icon(Icons.Filled.Search, contentDescription = "Cerca")
                }
                IconButton(onClick = {}) {
                    Icon(Icons.Filled.Favorite, contentDescription = "Preferiti")
                }
                IconButton(onClick = {}) {
                    Icon(Icons.Filled.Person, contentDescription = "Profilo")
                }
            }
        }
    ) { paddingValues ->
        Box(Modifier.padding(paddingValues)) { /* contenuto */ }
    }
}
```

---

## 4. COMPONENTI M3 STANDARD (AGGIORNATI CON STILE EXPRESSIVE)

### NavigationBar (Tab Bar inferiore)
```kotlin
@Composable
fun BottomNavExample() {
    var selectedItem by remember { mutableIntStateOf(0) }
    val items = listOf(
        Triple("Home", Icons.Filled.Home, Icons.Outlined.Home),
        Triple("Cerca", Icons.Filled.Search, Icons.Outlined.Search),
        Triple("Notifiche", Icons.Filled.Notifications, Icons.Outlined.Notifications),
        Triple("Profilo", Icons.Filled.Person, Icons.Outlined.Person)
    )

    NavigationBar {
        items.forEachIndexed { index, (titolo, iconaSelezionata, icona) ->
            NavigationBarItem(
                icon = {
                    Icon(
                        if (selectedItem == index) iconaSelezionata else icona,
                        contentDescription = titolo
                    )
                },
                label = { Text(titolo) },
                selected = selectedItem == index,
                onClick = { selectedItem = index }
            )
        }
    }
}
```

### TopAppBar varianti M3
```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopAppBarVarianti() {
    // CenterAligned (titolo centrato)
    CenterAlignedTopAppBar(
        title = { Text("Titolo Centrato") },
        navigationIcon = {
            IconButton(onClick = {}) { Icon(Icons.Default.ArrowBack, null) }
        },
        actions = {
            IconButton(onClick = {}) { Icon(Icons.Default.Share, null) }
        }
    )

    // Medium (titolo grande che collassa)
    MediumTopAppBar(
        title = { Text("Titolo Medium") },
        scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    )

    // Large (titolo molto grande che collassa)
    LargeTopAppBar(
        title = { Text("Titolo Grande") },
        scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    )
}
```

### Search Bar M3
```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBarExample() {
    var query by rememberSaveable { mutableStateOf("") }
    var active by rememberSaveable { mutableStateOf(false) }

    SearchBar(
        query = query,
        onQueryChange = { query = it },
        onSearch = {
            active = false
            // Esegui ricerca
        },
        active = active,
        onActiveChange = { active = it },
        placeholder = { Text("Cerca utenti...") },
        leadingIcon = { Icon(Icons.Default.Search, null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { query = "" }) {
                    Icon(Icons.Default.Clear, "Cancella")
                }
            }
        }
    ) {
        // Suggerimenti di ricerca
        listOf("Mario Rossi", "Luigi Verdi", "Anna Bianchi").forEach { suggerimento ->
            ListItem(
                headlineContent = { Text(suggerimento) },
                leadingContent = { Icon(Icons.Default.History, null) },
                modifier = Modifier
                    .clickable {
                        query = suggerimento
                        active = false
                    }
                    .fillMaxWidth()
            )
        }
    }
}
```

### Chips M3
```kotlin
@Composable
fun ChipExamples() {
    // Filter Chip (per filtri)
    var selezionato by remember { mutableStateOf(false) }
    FilterChip(
        selected = selezionato,
        onClick = { selezionato = !selezionato },
        label = { Text("Filtro") },
        leadingIcon = if (selezionato) {
            { Icon(Icons.Default.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }
        } else null
    )

    // Assist Chip
    AssistChip(
        onClick = { /* azione */ },
        label = { Text("Suggerimento") },
        leadingIcon = {
            Icon(Icons.Default.Lightbulb, null, Modifier.size(AssistChipDefaults.IconSize))
        }
    )

    // Input Chip (removibile)
    InputChip(
        selected = true,
        onClick = { /* azione */ },
        label = { Text("Tag") },
        trailingIcon = {
            Icon(Icons.Default.Close, null, Modifier.size(InputChipDefaults.IconSize))
        }
    )

    // Suggestion Chip
    SuggestionChip(
        onClick = { /* azione */ },
        label = { Text("Consigliato") }
    )

    // Fila di chip con FlowRow (wrap automatico)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val tags = listOf("Kotlin", "Compose", "M3", "Android", "Jetpack")
        tags.forEach { tag ->
            SuggestionChip(
                onClick = { /* seleziona tag */ },
                label = { Text(tag) }
            )
        }
    }
}
```

### Snackbar M3
```kotlin
@Composable
fun SnackbarExample() {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    actionColor = MaterialTheme.colorScheme.inversePrimary,
                    shape = MaterialTheme.shapes.medium
                )
            }
        }
    ) { padding ->
        Button(
            onClick = {
                scope.launch {
                    val result = snackbarHostState.showSnackbar(
                        message = "Elemento eliminato",
                        actionLabel = "Annulla",
                        duration = SnackbarDuration.Short
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        // L'utente ha premuto "Annulla"
                    }
                }
            },
            modifier = Modifier.padding(padding)
        ) {
            Text("Mostra Snackbar")
        }
    }
}
```

---

## 5. DYNAMIC COLOR (MATERIAL YOU)

```kotlin
@Composable
fun MiaAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,  // Abilita colori dinamici
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        // Colori dinamici (basati sullo sfondo dell'utente)
        // Disponibili da Android 12 (API 31+)
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        // Fallback a colori statici per device più vecchi
        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

// Token di colore M3 che DEVI usare (non colori hardcoded)
// Primari
MaterialTheme.colorScheme.primary
MaterialTheme.colorScheme.onPrimary
MaterialTheme.colorScheme.primaryContainer
MaterialTheme.colorScheme.onPrimaryContainer

// Secondari
MaterialTheme.colorScheme.secondary
MaterialTheme.colorScheme.onSecondary
MaterialTheme.colorScheme.secondaryContainer
MaterialTheme.colorScheme.onSecondaryContainer

// Terziari
MaterialTheme.colorScheme.tertiary
MaterialTheme.colorScheme.onTertiary
MaterialTheme.colorScheme.tertiaryContainer
MaterialTheme.colorScheme.onTertiaryContainer

// Superfici
MaterialTheme.colorScheme.surface
MaterialTheme.colorScheme.onSurface
MaterialTheme.colorScheme.surfaceVariant
MaterialTheme.colorScheme.onSurfaceVariant
MaterialTheme.colorScheme.surfaceContainerLowest
MaterialTheme.colorScheme.surfaceContainerLow
MaterialTheme.colorScheme.surfaceContainer
MaterialTheme.colorScheme.surfaceContainerHigh
MaterialTheme.colorScheme.surfaceContainerHighest

// Errore
MaterialTheme.colorScheme.error
MaterialTheme.colorScheme.onError
MaterialTheme.colorScheme.errorContainer
MaterialTheme.colorScheme.onErrorContainer

// Sfondo
MaterialTheme.colorScheme.background
MaterialTheme.colorScheme.onBackground

// Outline
MaterialTheme.colorScheme.outline
MaterialTheme.colorScheme.outlineVariant
```

---

## 6. SHAPE SYSTEM — FORME ESPRESSIVE

```kotlin
// M3 Expressive usa forme come linguaggio visivo
val shapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),    // Chip, piccoli elementi
    small = RoundedCornerShape(8.dp),         // Card compatte
    medium = RoundedCornerShape(12.dp),       // Card standard
    large = RoundedCornerShape(16.dp),        // FAB, dialoghi
    extraLarge = RoundedCornerShape(28.dp)    // Bottom sheets, grandi container
)

// Uso nei componenti
Card(shape = MaterialTheme.shapes.medium) { /* ... */ }
FloatingActionButton(shape = MaterialTheme.shapes.large, onClick = {}) { /* ... */ }

// Forme speciali
CircleShape        // Perfettamente circolare (avatar, icone)
RectangleShape     // Rettangolo senza arrotondamento

// Cut corner (angoli tagliati)
CutCornerShape(12.dp)

// Forma asimmetrica
RoundedCornerShape(
    topStart = 28.dp,
    topEnd = 28.dp,
    bottomStart = 0.dp,
    bottomEnd = 0.dp
)
```

---

## 7. TYPOGRAPHY SYSTEM

```kotlin
// M3 Expressive enfatizza la tipografia come elemento espressivo
val typography = Typography(
    // Display — per numeri grandi, hero text
    displayLarge = TextStyle(fontSize = 57.sp, lineHeight = 64.sp, fontWeight = FontWeight.Normal),
    displayMedium = TextStyle(fontSize = 45.sp, lineHeight = 52.sp),
    displaySmall = TextStyle(fontSize = 36.sp, lineHeight = 44.sp),

    // Headline — titoli di sezione
    headlineLarge = TextStyle(fontSize = 32.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 32.sp),

    // Title — titoli di componenti
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),

    // Body — contenuto principale
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),

    // Label — bottoni, chip, piccoli testi
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
)

// Uso nel codice — SEMPRE usare token del tema, mai valori hardcoded
Text(text = "Titolo", style = MaterialTheme.typography.headlineLarge)
Text(text = "Contenuto", style = MaterialTheme.typography.bodyMedium)
Text(text = "Etichetta", style = MaterialTheme.typography.labelSmall)
```

---

## 8. ADAPTIVE DESIGN — SCHERMI GRANDI E FOLDABLE

Android 16 richiede app adattive a schermi diversi.

```kotlin
// Rilevare la dimensione dello schermo
@Composable
fun AdaptiveLayout() {
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass

    when {
        windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.COMPACT -> {
            // Telefono — layout colonna singola
            PhoneLayout()
        }
        windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.MEDIUM -> {
            // Tablet piccolo / foldable — layout a due colonne
            TabletLayout()
        }
        windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.EXPANDED -> {
            // Tablet grande / desktop — layout completo
            DesktopLayout()
        }
    }
}

// NavigationSuiteScaffold — navigazione adattiva
// Si adatta automaticamente: Bottom bar su telefono, Rail su tablet, Drawer su desktop
@Composable
fun AdaptiveNavigationExample() {
    var selectedDestination by remember { mutableStateOf(Destination.HOME) }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            Destination.entries.forEach { destination ->
                item(
                    icon = { Icon(destination.icon, null) },
                    label = { Text(destination.label) },
                    selected = selectedDestination == destination,
                    onClick = { selectedDestination = destination }
                )
            }
        }
    ) {
        when (selectedDestination) {
            Destination.HOME -> HomeScreen()
            Destination.SEARCH -> SearchScreen()
            Destination.PROFILE -> ProfileScreen()
        }
    }
}

enum class Destination(val icon: ImageVector, val label: String) {
    HOME(Icons.Default.Home, "Home"),
    SEARCH(Icons.Default.Search, "Cerca"),
    PROFILE(Icons.Default.Person, "Profilo")
}
```

---

## 9. REGOLE DI DESIGN M3 EXPRESSIVE — LINEE GUIDA

### ✅ DA FARE
- Usa **SEMPRE** i token del tema (`MaterialTheme.colorScheme.*`, `MaterialTheme.typography.*`, `MaterialTheme.shapes.*`) — mai colori/font hardcoded
- Abilita `dynamicColor = true` per supportare Material You
- Usa animazioni `spring()` per interazioni — M3 Expressive è tutto spring-based
- Usa componenti M3 standard — sono già stilizzati con lo stile expressive
- Supporta dark mode e light mode
- Rendi l'app adattiva a schermi grandi (tablet, foldable, desktop mode)
- Usa `Scaffold` con `TopAppBar`, `NavigationBar`, `FAB`
- Usa `paddingValues` dal Scaffold per posizionare il contenuto
- Lascia spazio ai componenti (non affollare l'interfaccia)
- Usa elevazione e ombre per gerarchia visiva

### ❌ DA NON FARE
- NON replicare il design iOS (Liquid Glass, blur pesante, tab bar stile Apple)
- NON usare colori hardcoded — usa sempre i token del tema
- NON usare icone iOS (SF Symbols) — usa Material Icons
- NON mettere il back button a sinistra nel top bar se non c'è una navigazione stack
- NON usare font non-Material senza motivo
- NON ignorare il sistema di forme M3 (non usare angoli hardcoded)
- NON creare animazioni lineari — usa spring per coerenza con M3 Expressive

---

> **NOTA PER L'AI — MAPPATURA LIQUID GLASS (iOS) → MATERIAL 3 EXPRESSIVE (Android):**
> - Effetto glassmorphism/blur → **Surface con elevation e tonal color** (Android non usa blur per componenti standard)
> - Trasparenza vetro → **Surface container colors** con livelli di prominenza
> - Animazioni fluide iOS → **Spring animations** con `DampingRatioMediumBouncy`
> - Tab bar iOS (in basso) → **NavigationBar** M3
> - Navigation bar iOS (in alto) → **TopAppBar** M3
> - SF Symbols → **Material Icons** (`Icons.Default.*`, `Icons.Filled.*`, `Icons.Outlined.*`)
> - Contextual menu iOS → **DropdownMenu** M3
> - Action sheet iOS → **ModalBottomSheet** M3
> - Segmented control iOS → **Connected ButtonGroup** M3 Expressive
> - Pull to refresh iOS → **PullToRefreshBox** M3
> - Dynamic Island → **Non esiste equivalente** — usa notifiche e Live Updates
> - Haptic feedback → `HapticFeedback` via `LocalHapticFeedback.current`
> - Tint color iOS → `MaterialTheme.colorScheme.primary`
> - System background iOS → `MaterialTheme.colorScheme.background`
