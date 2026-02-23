# Architettura, Librerie e Struttura Progetto Android 2026

> Questo documento copre l'architettura raccomandata, le librerie Jetpack essenziali, il networking, il database locale, la dependency injection, e la struttura completa di un progetto Android nativo moderno.

---

## 1. ARCHITETTURA RACCOMANDATA: MVVM + Clean Architecture Light

```
┌─────────────────────────────────────────────────────┐
│                    UI Layer                          │
│  Composable Functions ← ViewModel (StateFlow)       │
│  (quello che l'utente vede)                         │
├─────────────────────────────────────────────────────┤
│                  Domain Layer (opzionale)            │
│  Use Cases / Interactors                            │
│  (logica di business pura)                          │
├─────────────────────────────────────────────────────┤
│                  Data Layer                          │
│  Repository → DataSource (Remote / Local)           │
│  (accesso ai dati)                                  │
└─────────────────────────────────────────────────────┘
```

### Struttura cartelle raccomandata
```
com.example.miaapp/
├── MainActivity.kt
├── MiaApp.kt                    // Root composable con navigazione
│
├── di/                          // Dependency Injection (Hilt modules)
│   ├── AppModule.kt
│   ├── NetworkModule.kt
│   └── DatabaseModule.kt
│
├── data/
│   ├── remote/
│   │   ├── api/
│   │   │   └── UtenteApi.kt        // Interfaccia Retrofit
│   │   └── dto/
│   │       └── UtenteDto.kt        // Data Transfer Object (da/a API)
│   ├── local/
│   │   ├── database/
│   │   │   ├── AppDatabase.kt
│   │   │   └── dao/
│   │   │       └── UtenteDao.kt
│   │   └── entity/
│   │       └── UtenteEntity.kt     // Entità Room
│   └── repository/
│       └── UtenteRepositoryImpl.kt
│
├── domain/
│   ├── model/
│   │   └── Utente.kt              // Modello di dominio
│   ├── repository/
│   │   └── UtenteRepository.kt    // Interfaccia repository
│   └── usecase/
│       ├── GetUtentiUseCase.kt
│       └── EliminaUtenteUseCase.kt
│
├── ui/
│   ├── theme/
│   │   ├── Theme.kt
│   │   ├── Color.kt
│   │   ├── Type.kt
│   │   └── Shape.kt
│   ├── components/                 // Componenti riusabili
│   │   ├── LoadingView.kt
│   │   ├── ErrorView.kt
│   │   └── UtenteCard.kt
│   ├── home/
│   │   ├── HomeScreen.kt
│   │   ├── HomeViewModel.kt
│   │   └── HomeUiState.kt
│   ├── detail/
│   │   ├── DetailScreen.kt
│   │   ├── DetailViewModel.kt
│   │   └── DetailUiState.kt
│   └── settings/
│       ├── SettingsScreen.kt
│       └── SettingsViewModel.kt
│
└── util/
    ├── Extensions.kt
    └── Constants.kt
```

---

## 2. VIEWMODEL — IN DETTAGLIO

```kotlin
// HomeUiState.kt — Stato dell'UI come data class immutabile
data class HomeUiState(
    val utenti: List<Utente> = emptyList(),
    val isLoading: Boolean = false,
    val errore: String? = null,
    val queryRicerca: String = "",
    val filtroAttivo: FiltroUtente = FiltroUtente.TUTTI
)

enum class FiltroUtente {
    TUTTI, ATTIVI, INATTIVI
}

// Eventi one-shot (navigazione, snackbar, toast)
sealed class HomeEvent {
    data class NavigaDettaglio(val utenteId: Int) : HomeEvent()
    data class MostraSnackbar(val messaggio: String) : HomeEvent()
    data object ScrollInAlto : HomeEvent()
}

// HomeViewModel.kt
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getUtentiUseCase: GetUtentiUseCase,
    private val eliminaUtenteUseCase: EliminaUtenteUseCase
) : ViewModel() {

    // Stato UI — osservabile da Compose
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    // Eventi one-shot
    private val _eventi = MutableSharedFlow<HomeEvent>()
    val eventi: SharedFlow<HomeEvent> = _eventi.asSharedFlow()

    // Ricerca con debounce
    private val _queryRicerca = MutableStateFlow("")

    init {
        caricaUtenti()
        osservaRicerca()
    }

    private fun osservaRicerca() {
        viewModelScope.launch {
            _queryRicerca
                .debounce(300)  // Aspetta 300ms dopo l'ultimo input
                .distinctUntilChanged()
                .collectLatest { query ->
                    if (query.isNotBlank()) {
                        cercaUtenti(query)
                    } else {
                        caricaUtenti()
                    }
                }
        }
    }

    fun caricaUtenti() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errore = null) }
            getUtentiUseCase()
                .catch { e ->
                    _uiState.update { it.copy(
                        isLoading = false,
                        errore = e.message ?: "Errore sconosciuto"
                    )}
                }
                .collect { utenti ->
                    _uiState.update { it.copy(
                        utenti = applicaFiltro(utenti, it.filtroAttivo),
                        isLoading = false
                    )}
                }
        }
    }

    fun aggiornaRicerca(query: String) {
        _uiState.update { it.copy(queryRicerca = query) }
        _queryRicerca.value = query
    }

    fun cambiaFiltro(filtro: FiltroUtente) {
        _uiState.update { stato ->
            stato.copy(
                filtroAttivo = filtro,
                utenti = applicaFiltro(stato.utenti, filtro)
            )
        }
    }

    fun eliminaUtente(utenteId: Int) {
        viewModelScope.launch {
            try {
                eliminaUtenteUseCase(utenteId)
                _eventi.emit(HomeEvent.MostraSnackbar("Utente eliminato"))
                caricaUtenti()
            } catch (e: Exception) {
                _eventi.emit(HomeEvent.MostraSnackbar("Errore: ${e.message}"))
            }
        }
    }

    private fun cercaUtenti(query: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val risultati = getUtentiUseCase.cerca(query)
                _uiState.update { it.copy(utenti = risultati, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errore = e.message) }
            }
        }
    }

    private fun applicaFiltro(utenti: List<Utente>, filtro: FiltroUtente): List<Utente> {
        return when (filtro) {
            FiltroUtente.TUTTI -> utenti
            FiltroUtente.ATTIVI -> utenti.filter { it.isAttivo }
            FiltroUtente.INATTIVI -> utenti.filter { !it.isAttivo }
        }
    }
}
```

---

## 3. DEPENDENCY INJECTION CON HILT

```kotlin
// MiaApp.kt (Application class)
@HiltAndroidApp
class MiaApp : Application()

// MainActivity.kt
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    // ...
}

// di/NetworkModule.kt
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        prettyPrint = false
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer ${TokenManager.token}")
                .build()
            chain.proceed(request)
        }
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://api.example.com/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun provideUtenteApi(retrofit: Retrofit): UtenteApi =
        retrofit.create(UtenteApi::class.java)
}

// di/DatabaseModule.kt
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "mia_app_database"
        )
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideUtenteDao(database: AppDatabase): UtenteDao =
        database.utenteDao()
}

// di/AppModule.kt
@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindUtenteRepository(
        impl: UtenteRepositoryImpl
    ): UtenteRepository
}
```

---

## 4. NETWORKING CON RETROFIT + KOTLINX SERIALIZATION

```kotlin
// data/remote/dto/UtenteDto.kt
@Serializable
data class UtenteDto(
    val id: Int,
    @SerialName("full_name") val nomeCompleto: String,
    val email: String,
    @SerialName("avatar_url") val avatarUrl: String?,
    @SerialName("is_active") val isAttivo: Boolean = true,
    @SerialName("created_at") val creatoIl: String
)

@Serializable
data class ApiResponse<T>(
    val data: T,
    val meta: MetaDto? = null
)

@Serializable
data class MetaDto(
    val page: Int,
    @SerialName("total_pages") val pagineTotali: Int,
    @SerialName("total_count") val contatoreTotale: Int
)

// data/remote/api/UtenteApi.kt
interface UtenteApi {

    @GET("users")
    suspend fun getUtenti(
        @Query("page") pagina: Int = 1,
        @Query("per_page") perPagina: Int = 20
    ): ApiResponse<List<UtenteDto>>

    @GET("users/{id}")
    suspend fun getUtente(@Path("id") id: Int): ApiResponse<UtenteDto>

    @POST("users")
    suspend fun creaUtente(@Body utente: UtenteDto): ApiResponse<UtenteDto>

    @PUT("users/{id}")
    suspend fun aggiornaUtente(
        @Path("id") id: Int,
        @Body utente: UtenteDto
    ): ApiResponse<UtenteDto>

    @DELETE("users/{id}")
    suspend fun eliminaUtente(@Path("id") id: Int)

    @GET("users/search")
    suspend fun cercaUtenti(@Query("q") query: String): ApiResponse<List<UtenteDto>>
}

// Mapping DTO → Domain Model
fun UtenteDto.toDomain(): Utente = Utente(
    id = id,
    nome = nomeCompleto,
    email = email,
    avatarUrl = avatarUrl,
    isAttivo = isAttivo
)

fun List<UtenteDto>.toDomain(): List<Utente> = map { it.toDomain() }
```

---

## 5. DATABASE LOCALE CON ROOM

```kotlin
// data/local/entity/UtenteEntity.kt
@Entity(tableName = "utenti")
data class UtenteEntity(
    @PrimaryKey val id: Int,
    val nome: String,
    val email: String,
    @ColumnInfo(name = "avatar_url") val avatarUrl: String?,
    @ColumnInfo(name = "is_attivo") val isAttivo: Boolean,
    @ColumnInfo(name = "ultimo_aggiornamento") val ultimoAggiornamento: Long = System.currentTimeMillis()
)

// Mapping Entity → Domain
fun UtenteEntity.toDomain(): Utente = Utente(
    id = id, nome = nome, email = email,
    avatarUrl = avatarUrl, isAttivo = isAttivo
)
fun Utente.toEntity(): UtenteEntity = UtenteEntity(
    id = id, nome = nome, email = email,
    avatarUrl = avatarUrl, isAttivo = isAttivo
)

// data/local/dao/UtenteDao.kt
@Dao
interface UtenteDao {
    @Query("SELECT * FROM utenti ORDER BY nome ASC")
    fun getUtenti(): Flow<List<UtenteEntity>>  // Flow = stream reattivo!

    @Query("SELECT * FROM utenti WHERE id = :id")
    suspend fun getUtente(id: Int): UtenteEntity?

    @Query("SELECT * FROM utenti WHERE nome LIKE '%' || :query || '%' OR email LIKE '%' || :query || '%'")
    suspend fun cerca(query: String): List<UtenteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserisci(utenti: List<UtenteEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserisci(utente: UtenteEntity)

    @Delete
    suspend fun elimina(utente: UtenteEntity)

    @Query("DELETE FROM utenti WHERE id = :id")
    suspend fun eliminaPerId(id: Int)

    @Query("DELETE FROM utenti")
    suspend fun eliminaTutti()
}

// data/local/database/AppDatabase.kt
@Database(
    entities = [UtenteEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun utenteDao(): UtenteDao
}
```

---

## 6. REPOSITORY PATTERN

```kotlin
// domain/repository/UtenteRepository.kt (interfaccia)
interface UtenteRepository {
    fun getUtenti(): Flow<List<Utente>>
    suspend fun getUtente(id: Int): Utente
    suspend fun cerca(query: String): List<Utente>
    suspend fun creaUtente(utente: Utente): Utente
    suspend fun aggiornaUtente(utente: Utente): Utente
    suspend fun eliminaUtente(id: Int)
    suspend fun refresh()
}

// data/repository/UtenteRepositoryImpl.kt
class UtenteRepositoryImpl @Inject constructor(
    private val api: UtenteApi,
    private val dao: UtenteDao
) : UtenteRepository {

    // Pattern "Single Source of Truth" — il database locale è la fonte unica di verità
    // La UI osserva il DB, e il repository aggiorna il DB dalla rete
    override fun getUtenti(): Flow<List<Utente>> {
        return dao.getUtenti()
            .map { entities -> entities.map { it.toDomain() } }
            .onStart {
                // All'inizio, prova a refreshare dalla rete
                try { refresh() } catch (_: Exception) { /* usa dati offline */ }
            }
    }

    override suspend fun getUtente(id: Int): Utente {
        // Prima prova dal DB locale
        dao.getUtente(id)?.let { return it.toDomain() }

        // Se non presente, carica dalla rete
        val dto = api.getUtente(id).data
        dao.inserisci(dto.toDomain().toEntity())
        return dto.toDomain()
    }

    override suspend fun cerca(query: String): List<Utente> {
        return try {
            // Cerca online
            val risultati = api.cercaUtenti(query).data
            risultati.toDomain()
        } catch (e: Exception) {
            // Fallback locale
            dao.cerca(query).map { it.toDomain() }
        }
    }

    override suspend fun creaUtente(utente: Utente): Utente {
        val dto = api.creaUtente(/* ... */).data
        dao.inserisci(dto.toDomain().toEntity())
        return dto.toDomain()
    }

    override suspend fun aggiornaUtente(utente: Utente): Utente {
        val dto = api.aggiornaUtente(utente.id, /* ... */).data
        dao.inserisci(dto.toDomain().toEntity())
        return dto.toDomain()
    }

    override suspend fun eliminaUtente(id: Int) {
        api.eliminaUtente(id)
        dao.eliminaPerId(id)
    }

    override suspend fun refresh() {
        val utenti = api.getUtenti().data
        dao.eliminaTutti()
        dao.inserisci(utenti.map { it.toDomain().toEntity() })
    }
}
```

---

## 7. USE CASE PATTERN (OPZIONALE MA CONSIGLIATO)

```kotlin
// domain/usecase/GetUtentiUseCase.kt
class GetUtentiUseCase @Inject constructor(
    private val repository: UtenteRepository
) {
    operator fun invoke(): Flow<List<Utente>> = repository.getUtenti()

    suspend fun cerca(query: String): List<Utente> = repository.cerca(query)
}

// domain/usecase/EliminaUtenteUseCase.kt
class EliminaUtenteUseCase @Inject constructor(
    private val repository: UtenteRepository
) {
    suspend operator fun invoke(utenteId: Int) {
        repository.eliminaUtente(utenteId)
    }
}

// Uso nel ViewModel — pulito e testabile
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getUtenti: GetUtentiUseCase,    // Nota: nomi brevi grazie a operator invoke
    private val eliminaUtente: EliminaUtenteUseCase
) : ViewModel() {
    fun carica() {
        viewModelScope.launch {
            getUtenti().collect { /* aggiorna stato */ }
        }
    }
    fun elimina(id: Int) {
        viewModelScope.launch {
            eliminaUtente(id)  // Grazie a operator fun invoke()
        }
    }
}
```

---

## 8. DATASTORE — PREFERENZE UTENTE (SOSTITUISCE SharedPreferences)

```kotlin
// di/DataStoreModule.kt
@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create {
            context.preferencesDataStoreFile("impostazioni")
        }
}

// data/local/PreferenzeManager.kt
class PreferenzeManager @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    // Chiavi
    private object Keys {
        val TEMA_SCURO = booleanPreferencesKey("tema_scuro")
        val LINGUA = stringPreferencesKey("lingua")
        val NOTIFICHE_ATTIVE = booleanPreferencesKey("notifiche_attive")
        val FONT_SIZE = floatPreferencesKey("font_size")
    }

    // Leggere (come Flow)
    val temaScuro: Flow<Boolean> = dataStore.data
        .map { preferenze -> preferenze[Keys.TEMA_SCURO] ?: false }

    val lingua: Flow<String> = dataStore.data
        .map { preferenze -> preferenze[Keys.LINGUA] ?: "it" }

    val notificheAttive: Flow<Boolean> = dataStore.data
        .map { preferenze -> preferenze[Keys.NOTIFICHE_ATTIVE] ?: true }

    // Scrivere
    suspend fun setTemaScuro(scuro: Boolean) {
        dataStore.edit { it[Keys.TEMA_SCURO] = scuro }
    }

    suspend fun setLingua(lingua: String) {
        dataStore.edit { it[Keys.LINGUA] = lingua }
    }

    suspend fun setNotificheAttive(attive: Boolean) {
        dataStore.edit { it[Keys.NOTIFICHE_ATTIVE] = attive }
    }
}

// Uso in un ViewModel
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferenze: PreferenzeManager
) : ViewModel() {
    val temaScuro = preferenze.temaScuro.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    fun cambiaTema(scuro: Boolean) {
        viewModelScope.launch {
            preferenze.setTemaScuro(scuro)
        }
    }
}
```

---

## 9. PERMESSI ANDROID

```kotlin
// Gestione permessi in Compose
@Composable
fun CameraScreen() {
    val context = LocalContext.current

    // Richiesta singolo permesso
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { concesso ->
        if (concesso) {
            // Permesso concesso — apri fotocamera
        } else {
            // Permesso negato
        }
    }

    // Richiesta multipli permessi
    val multiPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permessi ->
        val tuttoConcesso = permessi.values.all { it }
        if (tuttoConcesso) {
            // Tutti i permessi concessi
        }
    }

    Button(onClick = {
        when {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                // Già concesso
            }
            else -> {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }) {
        Text("Apri Fotocamera")
    }
}
```

---

## 10. MANIFEST ANDROID

```xml
<!-- AndroidManifest.xml -->
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- Permessi -->
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <application
        android:name=".MiaApp"
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.MiaApp"
        android:enableOnBackInvokedCallback="true">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:windowSoftInputMode="adjustResize"
            android:theme="@style/Theme.MiaApp">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

    </application>

</manifest>
```

---

## 11. TESTING

```kotlin
// Unit test del ViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var viewModel: HomeViewModel
    private val fakeRepository = FakeUtenteRepository()

    @Before
    fun setup() {
        viewModel = HomeViewModel(
            getUtentiUseCase = GetUtentiUseCase(fakeRepository),
            eliminaUtenteUseCase = EliminaUtenteUseCase(fakeRepository)
        )
    }

    @Test
    fun `caricaUtenti aggiorna stato con successo`() = runTest {
        // Given
        fakeRepository.setUtenti(listOf(
            Utente(1, "Mario", "mario@test.com", null, true)
        ))

        // When
        viewModel.caricaUtenti()

        // Then
        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(1, state.utenti.size)
        assertEquals("Mario", state.utenti.first().nome)
    }
}

// UI test con Compose
class HomeScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun mostraListaUtenti() {
        val uiState = HomeUiState(
            utenti = listOf(
                Utente(1, "Mario", "mario@test.com", null, true),
                Utente(2, "Luigi", "luigi@test.com", null, true)
            )
        )

        composeRule.setContent {
            MiaAppTheme {
                HomeContent(
                    uiState = uiState,
                    onCerca = {},
                    onRicarica = {},
                    onEliminaUtente = {},
                    onCliccaUtente = {}
                )
            }
        }

        composeRule.onNodeWithText("Mario").assertIsDisplayed()
        composeRule.onNodeWithText("Luigi").assertIsDisplayed()
    }

    @Test
    fun mostraLoadingIndicator() {
        val uiState = HomeUiState(isLoading = true)

        composeRule.setContent {
            MiaAppTheme {
                HomeContent(
                    uiState = uiState,
                    onCerca = {},
                    onRicarica = {},
                    onEliminaUtente = {},
                    onCliccaUtente = {}
                )
            }
        }

        composeRule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate))
            .assertIsDisplayed()
    }
}
```

---

## 12. PROGUARD / R8 RULES

```proguard
# proguard-rules.pro

# Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.example.miaapp.**$$serializer { *; }
-keepclassmembers class com.example.miaapp.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.miaapp.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Retrofit
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**
```

---

## 13. RIEPILOGO DIPENDENZE COMPLETE (libs.versions.toml)

```toml
[versions]
agp = "8.8.0"
kotlin = "2.1.10"
ksp = "2.1.10-1.0.29"
composeBom = "2025.12.00"
activityCompose = "1.10.0"
lifecycleRuntimeCompose = "2.9.0"
navigation3 = "1.0.0"
hilt = "2.54"
hiltNavigationCompose = "1.2.0"
room = "2.7.0"
retrofit = "2.11.0"
okhttp = "4.12.0"
kotlinxSerialization = "1.7.3"
coil = "3.1.0"
datastore = "1.1.2"

[libraries]
# Compose
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
compose-foundation = { group = "androidx.compose.foundation", name = "foundation" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
compose-material-icons = { group = "androidx.compose.material", name = "material-icons-extended" }

# Activity & Lifecycle
activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycleRuntimeCompose" }
lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycleRuntimeCompose" }

# Navigation 3
navigation3-runtime = { group = "androidx.navigation3", name = "navigation3-runtime", version.ref = "navigation3" }
navigation3-ui = { group = "androidx.navigation3", name = "navigation3-ui", version.ref = "navigation3" }

# Hilt
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-android-compiler = { group = "com.google.dagger", name = "hilt-android-compiler", version.ref = "hilt" }
hilt-navigation-compose = { group = "androidx.hilt", name = "hilt-navigation-compose", version.ref = "hiltNavigationCompose" }

# Room
room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }

# Networking
retrofit = { group = "com.squareup.retrofit2", name = "retrofit", version.ref = "retrofit" }
retrofit-kotlinx-serialization = { group = "com.squareup.retrofit2", name = "converter-kotlinx-serialization", version.ref = "retrofit" }
okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }
okhttp-logging = { group = "com.squareup.okhttp3", name = "logging-interceptor", version.ref = "okhttp" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "kotlinxSerialization" }

# Immagini
coil-compose = { group = "io.coil-kt.coil3", name = "coil-compose", version.ref = "coil" }

# DataStore
datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
```

---

> **NOTA PER L'AI — REGOLE DI ARCHITETTURA:**
> - Ogni schermata ha: `XScreen.kt` (Composable), `XViewModel.kt` (ViewModel), `XUiState.kt` (stato)
> - I ViewModel NON importano mai classi Android (Context, Activity, ecc.) — solo logica pura
> - I Composable NON fanno mai chiamate di rete o database direttamente — sempre via ViewModel
> - Il Repository è la SINGOLA FONTE DI VERITÀ per i dati
> - Usa `StateFlow` per stato UI osservabile, `SharedFlow` per eventi one-shot
> - Usa `collectAsStateWithLifecycle()` in Compose (NON `collectAsState()`)
> - Usa `Modifier` come primo parametro opzionale di ogni componente custom
> - Usa `key` nelle liste `LazyColumn` per performance e stabilità animazioni
> - Usa KSP (NON kapt) per Room, Hilt, e qualsiasi annotation processing
> - `enableEdgeToEdge()` è obbligatorio per Android 15+ (gestione insets)
> - Usa `WindowInsets` per gestire status bar, navigation bar, keyboard
