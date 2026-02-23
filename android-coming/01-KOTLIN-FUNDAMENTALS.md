# Kotlin — Guida Completa per lo Sviluppo Android Nativo (2026)

> Questo documento è una reference completa del linguaggio Kotlin, pensata per essere data a un'AI che deve sviluppare un'app Android nativa. Kotlin è il linguaggio ufficiale raccomandato da Google per Android. È l'equivalente di Swift per iOS.

---

## 1. SETUP E VERSIONE

- **Versione Kotlin raccomandata**: 2.1+ (febbraio 2026)
- **Build system**: Gradle con Kotlin DSL (build.gradle.kts)
- **Compilatore**: K2 compiler (default da Kotlin 2.0+, molto più veloce)
- **Annotation processing**: KSP (Kotlin Symbol Processing) — NON usare kapt, è deprecato e 2x più lento

---

## 2. TIPI BASE E VARIABILI

### Dichiarazione variabili
```kotlin
// Immutabile (equivalente di "let" in Swift)
val nome: String = "Mario"
val eta = 30  // Type inference

// Mutabile (equivalente di "var" in Swift)
var contatore: Int = 0
contatore += 1

// Costanti a livello di compilazione
const val MAX_RETRY = 3  // Solo in top-level o companion object
```

### Tipi primitivi
```kotlin
val intero: Int = 42
val lungo: Long = 42L
val decimale: Float = 3.14f
val doppio: Double = 3.14
val booleano: Boolean = true
val carattere: Char = 'A'
val stringa: String = "Ciao"
val byte: Byte = 127
val short: Short = 32767
```

### Stringhe e template
```kotlin
val nome = "Mondo"
val saluto = "Ciao, $nome!"  // String interpolation
val calcolo = "Risultato: ${2 + 3}"  // Espressioni dentro ${}

// Stringhe multilinea (equivalente di """ in Swift)
val testo = """
    Riga 1
    Riga 2
    Riga 3
""".trimIndent()

// Raw string
val regex = """\d{3}-\d{4}""".toRegex()
```

---

## 3. NULLABILITY (FONDAMENTALE)

Kotlin ha la null-safety integrata nel type system, esattamente come Swift con gli Optional.

```kotlin
// Non-null per default (equivalente di un tipo normale Swift)
var nome: String = "Mario"
// nome = null  // ERRORE DI COMPILAZIONE!

// Nullable (equivalente di String? in Swift)
var cognome: String? = "Rossi"
cognome = null  // OK

// Safe call operator (equivalente di ?. in Swift)
val lunghezza: Int? = cognome?.length

// Elvis operator (equivalente di ?? in Swift)
val lunghezzaSafe: Int = cognome?.length ?: 0

// Non-null assertion (equivalente di ! in Swift — EVITARE se possibile)
val lunghezzaForzata: Int = cognome!!.length  // Crash se null

// Smart cast — dopo un check, il compilatore sa che non è null
if (cognome != null) {
    // Qui cognome è automaticamente String (non String?)
    println(cognome.length)
}

// Safe cast
val valore: Any = "ciao"
val stringa: String? = valore as? String  // null se il cast fallisce

// let — esegue un blocco solo se non-null (come if let in Swift)
cognome?.let { nomeNonNull ->
    println("Il cognome è $nomeNonNull")
}

// Combinare più nullable
val risultato = utente?.indirizzo?.citta?.nome ?: "Sconosciuta"
```

---

## 4. FUNZIONI

### Dichiarazione base
```kotlin
// Funzione standard
fun somma(a: Int, b: Int): Int {
    return a + b
}

// Funzione single-expression (come Swift)
fun somma(a: Int, b: Int): Int = a + b

// Funzione senza valore di ritorno (Unit = Void in Swift)
fun stampa(messaggio: String) {
    println(messaggio)
}

// Parametri di default (come Swift)
fun saluta(nome: String, saluto: String = "Ciao") {
    println("$saluto, $nome!")
}

// Named arguments (come Swift)
saluta(nome = "Mario", saluto = "Buongiorno")

// Varargs (come ... in Swift)
fun sommaMultipla(vararg numeri: Int): Int = numeri.sum()
val totale = sommaMultipla(1, 2, 3, 4, 5)
```

### Lambda e Higher-Order Functions
```kotlin
// Lambda (equivalente di closure in Swift)
val raddoppia: (Int) -> Int = { numero -> numero * 2 }
val risultato = raddoppia(5)  // 10

// Lambda con tipo implicito
val triplica = { numero: Int -> numero * 3 }

// "it" — parametro implicito singolo (come $0 in Swift)
val quadruplica: (Int) -> Int = { it * 4 }

// Higher-order function
fun applicaOperazione(valore: Int, operazione: (Int) -> Int): Int {
    return operazione(valore)
}
val risultato = applicaOperazione(5) { it * 2 }

// Trailing lambda (come trailing closure in Swift)
listOf(1, 2, 3).map { it * 2 }  // [2, 4, 6]

// Lambda multi-riga
listOf(1, 2, 3).map { numero ->
    val raddoppiato = numero * 2
    raddoppiato + 1  // L'ultima espressione è il return value
}

// Lambda con destructuring
mapOf("a" to 1, "b" to 2).forEach { (chiave, valore) ->
    println("$chiave = $valore")
}
```

### Extension Functions (come le extension di Swift)
```kotlin
// Aggiungere metodi a tipi esistenti
fun String.capitalizzaPrimaLettera(): String {
    return this.replaceFirstChar { it.uppercase() }
}
val nome = "mario".capitalizzaPrimaLettera()  // "Mario"

// Extension property
val String.primoCarattere: Char
    get() = this[0]

// Extension function nullable
fun String?.oVuota(): String = this ?: ""
```

---

## 5. CLASSI E OGGETTI

### Classe base
```kotlin
// Classe semplice con primary constructor
class Persona(
    val nome: String,       // proprietà immutabile
    var eta: Int,           // proprietà mutabile
    val email: String = ""  // valore di default
) {
    // Proprietà calcolata (come computed property in Swift)
    val isAdulto: Boolean
        get() = eta >= 18

    // Proprietà con backing field
    var soprannome: String = ""
        set(value) {
            field = value.trim()  // "field" è il backing field
        }

    // Init block (equivalente di codice nell'init di Swift)
    init {
        require(eta >= 0) { "L'età non può essere negativa" }
    }

    // Secondary constructor
    constructor(nome: String) : this(nome, 0)

    // Metodi
    fun presentati(): String = "Sono $nome, ho $eta anni"

    // Override di toString
    override fun toString(): String = "Persona(nome=$nome, eta=$eta)"
}

// Uso
val persona = Persona("Mario", 30, "mario@email.com")
val persona2 = Persona(nome = "Luigi", eta = 25)
```

### Data Class (equivalente di struct con Equatable/Hashable in Swift)
```kotlin
// Genera automaticamente: equals(), hashCode(), toString(), copy(), componentN()
data class Utente(
    val id: Int,
    val nome: String,
    val email: String,
    val isAttivo: Boolean = true
)

val utente1 = Utente(1, "Mario", "mario@email.com")
val utente2 = utente1.copy(nome = "Luigi")  // Copia con modifica
val (id, nome, email) = utente1  // Destructuring

// Comparazione per valore (come struct Swift)
println(utente1 == utente1.copy())  // true
```

### Ereditarietà
```kotlin
// In Kotlin le classi sono final per default. Usare "open" per permettere ereditarietà
open class Animale(val nome: String) {
    open fun verso(): String = "..."

    // Metodo final (non sovrascrivibile ulteriormente)
    fun respira() = println("$nome respira")
}

class Cane(nome: String, val razza: String) : Animale(nome) {
    override fun verso(): String = "Bau!"
}

class Gatto(nome: String) : Animale(nome) {
    override fun verso(): String = "Miao!"
}
```

### Abstract e Interface
```kotlin
// Classe astratta (come una class astratta in Swift)
abstract class Forma {
    abstract val area: Double
    abstract fun perimetro(): Double

    // Può avere implementazioni concrete
    fun descrizione(): String = "Forma con area $area"
}

// Interface (equivalente di protocol in Swift)
interface Disegnabile {
    fun disegna()

    // Le interface possono avere implementazioni default
    fun colore(): String = "nero"
}

interface Animabile {
    fun anima(durata: Long)
}

// Implementazione multipla
class Cerchio(val raggio: Double) : Forma(), Disegnabile, Animabile {
    override val area: Double
        get() = Math.PI * raggio * raggio

    override fun perimetro(): Double = 2 * Math.PI * raggio
    override fun disegna() = println("Disegno un cerchio di raggio $raggio")
    override fun anima(durata: Long) = println("Animo per $durata ms")
}
```

### Sealed Class (equivalente di enum con associated values in Swift)
```kotlin
// FONDAMENTALE per gestire stati in Android/Compose
sealed class UiState<out T> {
    data object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val messaggio: String, val throwable: Throwable? = null) : UiState<Nothing>()
}

// Uso con when (equivalente di switch in Swift) — IL COMPILATORE VERIFICA CHE SIA EXHAUSTIVE
fun <T> gestisciStato(stato: UiState<T>) {
    when (stato) {
        is UiState.Loading -> println("Caricamento...")
        is UiState.Success -> println("Dati: ${stato.data}")  // Smart cast!
        is UiState.Error -> println("Errore: ${stato.messaggio}")
    }
}

// Sealed class per navigazione
sealed class Screen {
    data object Home : Screen()
    data object Settings : Screen()
    data class Detail(val id: String) : Screen()
    data class Profile(val userId: Int, val tab: String = "info") : Screen()
}

// Sealed interface (più flessibile, permette implementazione multipla)
sealed interface Result<out T> {
    data class Ok<T>(val value: T) : Result<T>
    data class Err(val error: Throwable) : Result<Nothing>
}
```

### Object e Companion Object
```kotlin
// Singleton (equivalente di un singleton Swift)
object AppConfig {
    var isDarkMode: Boolean = false
    val versione: String = "1.0.0"

    fun reset() {
        isDarkMode = false
    }
}
// Uso: AppConfig.isDarkMode = true

// Companion object (equivalente di static in Swift)
class Utente private constructor(val nome: String) {
    companion object {
        // "Factory method"
        fun crea(nome: String): Utente {
            require(nome.isNotBlank())
            return Utente(nome.trim())
        }

        const val MAX_NOME_LENGTH = 50
    }
}
val utente = Utente.crea("Mario")
```

### Enum Class
```kotlin
enum class Priorita(val livello: Int) {
    BASSA(1),
    MEDIA(2),
    ALTA(3),
    CRITICA(4);

    fun descrizione(): String = when (this) {
        BASSA -> "Può aspettare"
        MEDIA -> "Da fare"
        ALTA -> "Urgente"
        CRITICA -> "Immediato!"
    }
}

val priorita = Priorita.ALTA
println(priorita.livello)       // 3
println(priorita.descrizione()) // "Urgente"
```

### Value Class (ottimizzazione per wrapping)
```kotlin
@JvmInline
value class Email(val valore: String) {
    init {
        require(valore.contains("@")) { "Email non valida" }
    }
}

@JvmInline
value class UserId(val id: Long)

// Nessun overhead a runtime — compilato come tipo primitivo
fun inviaEmail(destinatario: Email, mittente: Email) { /* ... */ }
```

---

## 6. COLLEZIONI

```kotlin
// Liste immutabili (default — PREFERIRE SEMPRE)
val numeri: List<Int> = listOf(1, 2, 3, 4, 5)
val vuota: List<String> = emptyList()

// Liste mutabili
val numeriMutabili: MutableList<Int> = mutableListOf(1, 2, 3)
numeriMutabili.add(4)
numeriMutabili.removeAt(0)

// Mappe immutabili
val mappa: Map<String, Int> = mapOf("uno" to 1, "due" to 2)

// Mappe mutabili
val mappaMutabile: MutableMap<String, Int> = mutableMapOf()
mappaMutabile["tre"] = 3

// Set
val set: Set<String> = setOf("a", "b", "c")
val setMutabile: MutableSet<String> = mutableSetOf("a", "b")

// Operazioni funzionali su collezioni (USATISSIME in Android)
val risultati = numeri
    .filter { it > 2 }           // [3, 4, 5]
    .map { it * 10 }             // [30, 40, 50]
    .sortedDescending()           // [50, 40, 30]
    .take(2)                      // [50, 40]

val somma = numeri.sum()                     // 15
val media = numeri.average()                 // 3.0
val primo = numeri.firstOrNull { it > 3 }   // 4
val tutti = numeri.all { it > 0 }           // true
val qualcuno = numeri.any { it > 4 }        // true
val nessuno = numeri.none { it > 10 }       // true

// groupBy
data class Persona(val nome: String, val citta: String)
val persone = listOf(
    Persona("Mario", "Roma"),
    Persona("Luigi", "Roma"),
    Persona("Anna", "Milano")
)
val perCitta: Map<String, List<Persona>> = persone.groupBy { it.citta }

// associate / associateBy
val perNome: Map<String, Persona> = persone.associateBy { it.nome }

// flatMap
val liste = listOf(listOf(1, 2), listOf(3, 4))
val piatta = liste.flatMap { it }  // [1, 2, 3, 4]
// oppure
val piatta2 = liste.flatten()

// fold / reduce
val prodotto = numeri.fold(1) { acc, n -> acc * n }  // 120

// zip
val nomi = listOf("Mario", "Luigi")
val eta = listOf(30, 25)
val coppie: List<Pair<String, Int>> = nomi.zip(eta)  // [(Mario, 30), (Luigi, 25)]

// chunked / windowed
val blocchi = (1..10).chunked(3)  // [[1,2,3], [4,5,6], [7,8,9], [10]]
```

---

## 7. COROUTINES (EQUIVALENTE DI ASYNC/AWAIT SWIFT)

Le coroutines sono il sistema di concorrenza di Kotlin. Sono FONDAMENTALI per Android.

```kotlin
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

// ---- SUSPEND FUNCTIONS ----
// Una suspend function può essere "sospesa" senza bloccare il thread
// Equivalente di una "async function" in Swift
suspend fun caricaDati(): List<String> {
    delay(1000)  // Sospende senza bloccare (come Task.sleep in Swift)
    return listOf("dato1", "dato2")
}

suspend fun caricaUtente(id: Int): Utente {
    return withContext(Dispatchers.IO) {  // Cambia al thread IO
        // Simulazione chiamata di rete
        delay(500)
        Utente(id, "Mario", "mario@email.com")
    }
}

// ---- DISPATCHERS (equivalenti dei DispatchQueue in Swift) ----
// Dispatchers.Main      -> Thread principale UI (come DispatchQueue.main)
// Dispatchers.IO        -> Thread per operazioni I/O (rete, database, file)
// Dispatchers.Default   -> Thread per calcoli CPU-intensive

// ---- SCOPE ----
// In Android, si usa viewModelScope o lifecycleScope
// Nel ViewModel:
class MioViewModel : ViewModel() {
    fun carica() {
        viewModelScope.launch {  // Si cancella automaticamente col ViewModel
            val utente = caricaUtente(1)
            // Aggiorna UI state
        }
    }
}

// ---- PARALLELISMO ----
suspend fun caricaTutto() = coroutineScope {
    // async/await per esecuzione parallela
    val utente = async { caricaUtente(1) }
    val post = async { caricaPost(1) }

    // Aspetta entrambi
    val risultatoUtente = utente.await()
    val risultatoPost = post.await()

    // Oppure in un colpo solo
    val (u, p) = awaitAll(
        async { caricaUtente(1) },
        async { caricaPost(1) }
    )
}

// ---- FLOW (equivalente di AsyncSequence / Combine Publisher in Swift) ----
// Flow è il tipo reattivo di Kotlin per stream di dati
fun contatoreFlow(): Flow<Int> = flow {
    var i = 0
    while (true) {
        emit(i++)         // Emette un valore
        delay(1000)       // Aspetta 1 secondo
    }
}

// Operatori su Flow
val flussoFiltrato = contatoreFlow()
    .filter { it % 2 == 0 }      // Solo pari
    .map { "Valore: $it" }       // Trasforma
    .take(10)                     // Prendi solo 10
    .catch { e -> emit("Errore: ${e.message}") }  // Gestione errori

// StateFlow (equivalente di @Published / CurrentValueSubject in Swift)
// USATISSIMO nei ViewModel per esporre stato alla UI
class MioViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<UiState<List<Utente>>>(UiState.Loading)
    val uiState: StateFlow<UiState<List<Utente>>> = _uiState.asStateFlow()

    fun caricaUtenti() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val utenti = repository.getUtenti()
                _uiState.value = UiState.Success(utenti)
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Errore sconosciuto")
            }
        }
    }
}

// SharedFlow (equivalente di PassthroughSubject — eventi one-shot)
class MioViewModel : ViewModel() {
    private val _eventi = MutableSharedFlow<EventoUi>()
    val eventi: SharedFlow<EventoUi> = _eventi.asSharedFlow()

    fun mostraToast(messaggio: String) {
        viewModelScope.launch {
            _eventi.emit(EventoUi.MostraToast(messaggio))
        }
    }
}

// Collezionare un Flow in Compose
@Composable
fun MiaSchermata(viewModel: MioViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // collectAsStateWithLifecycle è PREFERITO rispetto a collectAsState
    // perché rispetta il ciclo di vita dell'activity/fragment
}

// ---- GESTIONE ERRORI ----
suspend fun operazioneSicura(): Result<Utente> {
    return try {
        val utente = caricaUtente(1)
        Result.success(utente)
    } catch (e: Exception) {
        Result.failure(e)
    }
}

// runCatching (syntax sugar per try/catch con Result)
suspend fun operazioneSicura2(): Result<Utente> = runCatching {
    caricaUtente(1)
}

// Uso di Result
operazioneSicura().fold(
    onSuccess = { utente -> println("Caricato: ${utente.nome}") },
    onFailure = { errore -> println("Errore: ${errore.message}") }
)
```

---

## 8. GENERICS

```kotlin
// Classe generica
class Contenitore<T>(val valore: T) {
    fun mappa<R>(trasformazione: (T) -> R): Contenitore<R> {
        return Contenitore(trasformazione(valore))
    }
}

// Funzione generica
fun <T> primoONull(lista: List<T>): T? = lista.firstOrNull()

// Constraint (equivalente di where T: Protocol in Swift)
fun <T : Comparable<T>> massimo(a: T, b: T): T = if (a > b) a else b

// Multiple constraints
fun <T> ordina(lista: List<T>) where T : Comparable<T>, T : Serializable {
    lista.sorted()
}

// Variance
// out = covariant (equivalente di associatedtype Output in Swift)
// in = contravariant
interface Produttore<out T> {
    fun produci(): T
}
interface Consumatore<in T> {
    fun consuma(item: T)
}

// reified type (tipo reale disponibile a runtime — NON esiste in Swift)
inline fun <reified T> isInstanceOf(value: Any): Boolean = value is T
```

---

## 9. SCOPE FUNCTIONS

Le scope functions sono un pattern Kotlin molto usato. Non hanno equivalente diretto in Swift.

```kotlin
val utente = Utente("Mario", "mario@email.com")

// let — trasforma e ritorna (come map su Optional in Swift)
val lunghezzaNome = utente.nome.let { it.length }

// also — fa un'azione collaterale, ritorna l'oggetto originale
val utente2 = utente.also { println("Creato utente: ${it.nome}") }

// apply — configura un oggetto, ritorna l'oggetto stesso
val paint = Paint().apply {
    color = Color.RED
    strokeWidth = 5f
    style = Paint.Style.STROKE
}

// run — esegue un blocco sull'oggetto, ritorna il risultato
val descrizione = utente.run {
    "Utente: $nome ($email)"
}

// with — come run ma non è extension function
val descrizione2 = with(utente) {
    "Utente: $nome ($email)"
}

// buildList / buildMap / buildString (scope builder)
val lista = buildList {
    add("primo")
    add("secondo")
    if (condizione) add("terzo")
}

val stringa = buildString {
    append("Hello")
    append(" ")
    append("World")
}
```

---

## 10. DELEGATED PROPERTIES

```kotlin
import kotlin.properties.Delegates

// lazy — inizializzazione pigra (come lazy var in Swift)
val databasePesante: Database by lazy {
    Database.crea()  // Eseguito solo al primo accesso
}

// observable — notifica quando cambia
var nome: String by Delegates.observable("iniziale") { _, vecchio, nuovo ->
    println("Cambiato da $vecchio a $nuovo")
}

// vetoable — può rifiutare il cambio
var eta: Int by Delegates.vetoable(0) { _, _, nuovo ->
    nuovo >= 0  // Accetta solo valori non negativi
}

// Custom delegate
class Preferenza<T>(
    private val chiave: String,
    private val default: T
) : ReadWriteProperty<Any?, T> {
    override fun getValue(thisRef: Any?, property: KProperty<*>): T {
        // Leggi da SharedPreferences
        return default
    }
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        // Scrivi in SharedPreferences
    }
}

// Uso
var tema: String by Preferenza("tema", "chiaro")
```

---

## 11. KOTLIN SERIALIZATION (EQUIVALENTE DI CODABLE IN SWIFT)

```kotlin
// build.gradle.kts
// plugins { kotlin("plugin.serialization") version "2.1.0" }
// implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

import kotlinx.serialization.*
import kotlinx.serialization.json.*

@Serializable
data class Utente(
    val id: Int,
    val nome: String,
    val email: String,
    @SerialName("is_attivo") val isAttivo: Boolean = true,  // Nome JSON diverso
    val ruolo: Ruolo = Ruolo.UTENTE
)

@Serializable
enum class Ruolo {
    @SerialName("admin") ADMIN,
    @SerialName("utente") UTENTE,
    @SerialName("moderatore") MODERATORE
}

// Serializzazione
val utente = Utente(1, "Mario", "mario@email.com")
val json = Json.encodeToString(utente)
// {"id":1,"nome":"Mario","email":"mario@email.com","is_attivo":true,"ruolo":"utente"}

// Deserializzazione
val utenteDecodificato = Json.decodeFromString<Utente>(json)

// Configurazione JSON personalizzata
val jsonConfig = Json {
    ignoreUnknownKeys = true    // Ignora chiavi sconosciute
    prettyPrint = true          // Formattazione leggibile
    isLenient = true            // Più permissivo nel parsing
    coerceInputValues = true    // Usa valori default per null
    encodeDefaults = false      // Non serializza valori di default
}

// Sealed class serializzabile (per polimorfismo)
@Serializable
sealed class Risposta {
    @Serializable
    @SerialName("successo")
    data class Successo(val dati: List<Utente>) : Risposta()

    @Serializable
    @SerialName("errore")
    data class Errore(val codice: Int, val messaggio: String) : Risposta()
}
```

---

## 12. PATTERN COMUNI IN ANDROID

### Result wrapper pattern
```kotlin
sealed class Resource<out T> {
    data object Loading : Resource<Nothing>()
    data class Success<T>(val data: T) : Resource<T>()
    data class Error(
        val message: String,
        val throwable: Throwable? = null
    ) : Resource<Nothing>()

    val isLoading get() = this is Loading
    val isSuccess get() = this is Success
    val isError get() = this is Error

    fun <R> map(transform: (T) -> R): Resource<R> = when (this) {
        is Loading -> Loading
        is Success -> Success(transform(data))
        is Error -> Error(message, throwable)
    }
}
```

### Type alias
```kotlin
typealias UserId = Long
typealias UserMap = Map<UserId, Utente>
typealias ClickHandler = () -> Unit
typealias AsyncResult<T> = Flow<Resource<T>>
```

### DSL Builder pattern
```kotlin
// Kotlin permette di creare DSL leggibili grazie a lambda con receiver
class NotificaBuilder {
    var titolo: String = ""
    var messaggio: String = ""
    var priorita: Priorita = Priorita.MEDIA
    private val azioni = mutableListOf<Azione>()

    fun azione(nome: String, onClick: () -> Unit) {
        azioni.add(Azione(nome, onClick))
    }

    fun build(): Notifica = Notifica(titolo, messaggio, priorita, azioni)
}

fun notifica(block: NotificaBuilder.() -> Unit): Notifica {
    return NotificaBuilder().apply(block).build()
}

// Uso (legge come un DSL dichiarativo)
val n = notifica {
    titolo = "Nuovo messaggio"
    messaggio = "Hai ricevuto un messaggio da Mario"
    priorita = Priorita.ALTA
    azione("Rispondi") { /* ... */ }
    azione("Ignora") { /* ... */ }
}
```

---

> **NOTA PER L'AI**: Quando converti codice Swift in Kotlin, ricorda:
> - `let` Swift → `val` Kotlin
> - `var` Swift → `var` Kotlin
> - `guard let` Swift → `?: return` o `requireNotNull()` Kotlin
> - `if let x = optional` Swift → `optional?.let { x -> }` o `if (optional != null)` Kotlin
> - `switch` Swift → `when` Kotlin (più potente e conciso)
> - `struct` Swift → `data class` Kotlin
> - `protocol` Swift → `interface` Kotlin
> - `enum con associated values` Swift → `sealed class` Kotlin
> - `@Published` Swift → `MutableStateFlow` Kotlin
> - `async/await` Swift → `suspend fun` + `coroutineScope` Kotlin
> - `AsyncSequence` Swift → `Flow` Kotlin
> - `Codable` Swift → `@Serializable` Kotlin (kotlinx.serialization)
> - `[String]` Swift → `List<String>` Kotlin
> - `[String: Int]` Swift → `Map<String, Int>` Kotlin
> - `extension` Swift → `fun Tipo.metodo()` Kotlin extension function
> - `@escaping` Swift closure → non necessario in Kotlin
> - `weak self` Swift → non necessario in Kotlin (le coroutines gestiscono la memoria diversamente)
