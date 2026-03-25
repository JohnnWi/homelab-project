import Foundation

private let BESZEL_MB_TO_BYTES: Double = 1024.0 * 1024.0

struct BeszelSystem: Codable, Identifiable {
    let id: String
    let collectionId: String?
    let collectionName: String?
    let name: String
    let host: String
    var port: Int?
    let status: String
    let info: BeszelSystemInfo?
    let created: String?
    let updated: String?

    var isOnline: Bool { status == "up" }

    enum CodingKeys: String, CodingKey {
        case id, collectionId, collectionName, name, host, port, status, info, created, updated
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decode(String.self, forKey: .id)
        collectionId = try container.decodeIfPresent(String.self, forKey: .collectionId)
        collectionName = try container.decodeIfPresent(String.self, forKey: .collectionName)
        name = try container.decode(String.self, forKey: .name)
        host = try container.decode(String.self, forKey: .host)

        if let p = try? container.decodeIfPresent(Int.self, forKey: .port) {
            port = p
        } else if let s = try? container.decodeIfPresent(String.self, forKey: .port) {
            port = Int(s)
        }

        status = try container.decode(String.self, forKey: .status)
        info = try container.decodeIfPresent(BeszelSystemInfo.self, forKey: .info)
        created = try container.decodeIfPresent(String.self, forKey: .created)
        updated = try container.decodeIfPresent(String.self, forKey: .updated)
    }
}

struct BeszelSystemInfo: Codable {
    // All fields optional with defaults — API may omit fields for offline systems
    let cpu: Double?
    let mp: Double?       // Memory %
    let m: Double?        // Memory used (GB)
    let mt: Double?       // Memory total (GB)
    let dp: Double?       // Disk %
    let d: Double?        // Disk used (GB)
    let du: Double?       // Disk used (Beszel >=0.9)
    let dt: Double?       // Disk total (GB)
    let ns: Double?       // Network sent (MB/s)
    let nr: Double?       // Network received (MB/s)
    let u: Double?        // Uptime (seconds)
    let cm: String?       // CPU model
    let os: String?       // Operating system
    let k: String?        // Kernel
    let h: String?        // Hostname
    let t: Double?        // Temperature
    let c: Int?           // CPU cores
    let efs: [String: Double?]?  // External filesystem usage percentages

    // Safe accessors with defaults
    var cpuValue: Double { cpu ?? 0 }
    var mpValue: Double { mp ?? 0 }
    var mValue: Double { m ?? 0 }
    var mtValue: Double { mt ?? 0 }
    var dpValue: Double { dp ?? 0 }
    var dValue: Double { d ?? 0 }
    var duValue: Double { du ?? 0 }
    var dtValue: Double { dt ?? 0 }
    var nsValue: Double { ns ?? 0 }
    var nrValue: Double { nr ?? 0 }
    var uValue: Double { u ?? 0 }

    var overallDiskPercent: Double {
        min(max(dpValue, 0), 100)
    }

    static let empty = BeszelSystemInfo(cpu: nil, mp: nil, m: nil, mt: nil, dp: nil, d: nil, du: nil, dt: nil, ns: nil, nr: nil, u: nil, cm: nil, os: nil, k: nil, h: nil, t: nil, c: nil, efs: nil)
}

struct BeszelSystemsResponse: Codable {
    let items: [BeszelSystem]
    let totalItems: Int?
    let page: Int?
    let perPage: Int?
}

struct BeszelSystemRecord: Codable, Identifiable {
    let id: String
    let system: String?
    let stats: BeszelRecordStats
    let created: String?
    let updated: String?
}

// MARK: - Temperature Data (flexible decoding: object, array, or number)

enum BeszelTemperatureData: Codable {
    case sensors([String: Double])
    case array([Double])
    case single(Double)

    init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        if let obj = try? container.decode([String: Double].self) {
            self = .sensors(obj)
        } else if let arr = try? container.decode([Double].self) {
            self = .array(arr)
        } else if let val = try? container.decode(Double.self) {
            self = .single(val)
        } else {
            self = .array([])
        }
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        switch self {
        case .sensors(let map): try container.encode(map)
        case .array(let arr): try container.encode(arr)
        case .single(let val): try container.encode(val)
        }
    }

    var maxCelsius: Double? {
        switch self {
        case .sensors(let map): return map.values.max()
        case .array(let arr): return arr.max()
        case .single(let val): return val
        }
    }

    var sensorMap: [String: Double] {
        switch self {
        case .sensors(let map): return map
        case .array(let arr):
            var result: [String: Double] = [:]
            for (i, v) in arr.enumerated() { result["Sensor \(i)"] = v }
            return result
        case .single(let val): return ["Temperature": val]
        }
    }
}

// MARK: - Record Stats

struct BeszelRecordStats: Codable {
    let cpu: Double?
    let mp: Double?
    let m: Double?        // Memory total (GB) or memory used depending on version
    let mu: Double?       // Memory used (GB)
    let mb: Double?       // Memory buffer (GB)
    let mt: Double?       // Memory total (GB)
    let s: Double?        // Swap total (GB)
    let su: Double?       // Swap used (GB)
    let dp: Double?       // Disk %
    let d: Double?        // Disk total (GB)
    let du: Double?       // Disk used (GB)
    let dt: Double?       // Disk total legacy (GB)
    let dr: Double?       // Disk read bytes/s
    let dw: Double?       // Disk write bytes/s
    let b: [Double]?      // Bandwidth array [up, down] bytes/s
    let ns: Double?       // Network sent (MB/s)
    let nr: Double?       // Network received (MB/s)
    let t: BeszelTemperatureData?
    let efs: [String: BeszelFsEntry]?  // External filesystems
    let la: [Double]?     // Load average [1m, 5m, 15m]
    let ni: [String: [Double]]?  // Network interfaces
    let dio: [Double]?    // Disk I/O [read, write]
    let bat: [Double]?    // Battery [level%, minutes]
    let cpub: [Double]?   // CPU breakdown [user, system, nice, wait, idle]
    let cpus: [Double]?   // Per-core CPU %
    let dc: [BeszelContainer]?  // Docker containers
    let g: [String: BeszelGpuEntry]?  // GPU data

    var cpuValue: Double { cpu ?? 0 }
    var mpValue: Double { mp ?? 0 }
    var mValue: Double { m ?? 0 }
    var dpValue: Double { dp ?? 0 }
    var dValue: Double { d ?? 0 }
    var duValue: Double { du ?? 0 }

    var memoryUsedGb: Double? {
        if let mu { return mu }
        if let total = mt ?? m, let mp { return total * mp / 100.0 }
        return nil
    }

    var memoryTotalGb: Double? {
        if let mt, mt > 0 { return mt }
        return m.flatMap { $0 > 0 ? $0 : nil }
    }

    var swapUsedGb: Double? {
        su.flatMap { $0 >= 0 ? $0 : nil }
    }

    var swapTotalGb: Double? {
        s.flatMap { $0 > 0 ? $0 : nil }
    }

    var maxTempCelsius: Double? { t?.maxCelsius }

    var temperatureSensors: [String: Double] { t?.sensorMap ?? [:] }

    var loadAvgValues: [Double] { la ?? [] }

    var cpuBreakdownValues: [Double] { cpub ?? [] }

    var cpuCoreUsageValues: [Double] { cpus ?? [] }

    var networkInterfaces: [String: BeszelNetworkInterface] {
        guard let ni else { return [:] }
        var result: [String: BeszelNetworkInterface] = [:]
        for (name, values) in ni {
            guard values.count >= 2 else { continue }
            result[name] = BeszelNetworkInterface(
                uploadRateBytesPerSec: values[0],
                downloadRateBytesPerSec: values[1],
                uploadTotalBytes: values.count > 2 ? values[2] : nil,
                downloadTotalBytes: values.count > 3 ? values[3] : nil
            )
        }
        return result
    }

    var bandwidthUpBytesPerSec: Double? {
        b?.first ?? ns.map { $0 * BESZEL_MB_TO_BYTES }
    }

    var bandwidthDownBytesPerSec: Double? {
        (b?.count ?? 0) > 1 ? b![1] : nr.map { $0 * BESZEL_MB_TO_BYTES }
    }

    var diskReadBytesPerSec: Double? {
        dr ?? dio?.first
    }

    var diskWriteBytesPerSec: Double? {
        dw ?? ((dio?.count ?? 0) > 1 ? dio![1] : nil)
    }

    var batteryLevel: Int? {
        bat?.first.map { Int($0) }
    }

    var batteryMinutes: Int? {
        (bat?.count ?? 0) > 1 ? Int(bat![1]) : nil
    }

    var primaryGpu: BeszelGpuEntry? {
        g?.values.first
    }

    /// All GPU entries sorted by key (e.g. "0", "1", …)
    var gpuEntries: [(key: String, gpu: BeszelGpuEntry)] {
        guard let g else { return [] }
        return g.sorted(by: { $0.key < $1.key }).map { (key: $0.key, gpu: $0.value) }
    }

    var gpuUsagePercent: Double? { primaryGpu?.u }
    var gpuPowerWatts: Double? { primaryGpu?.p }
    var gpuVramPercent: Double? { primaryGpu?.memUsagePercent }
    var gpuVramUsedMb: Double? { primaryGpu?.mu }
    var gpuVramTotalMb: Double? { primaryGpu?.mt }

    func gpuUsagePercent(forKey key: String) -> Double? {
        g?[key]?.u
    }

    func gpuPowerWatts(forKey key: String) -> Double? {
        g?[key]?.p
    }

    func gpuVramPercent(forKey key: String) -> Double? {
        g?[key]?.memUsagePercent
    }
}

// MARK: - GPU Entry

struct BeszelGpuEntry: Codable {
    let n: String       // name
    let u: Double?      // usage %
    let p: Double?      // power watts
    let mu: Double?     // VRAM used MB
    let mt: Double?     // VRAM total MB

    var memUsedMb: Double { mu ?? 0 }
    var memTotalMb: Double { mt ?? 0 }

    var memUsagePercent: Double? {
        guard let mt, mt > 0, let mu else { return nil }
        return min(max(mu / mt * 100.0, 0), 100)
    }
}

// MARK: - Filesystem Entry

struct BeszelFsEntry: Codable {
    let d: Double?      // total GB
    let du: Double?     // used GB
    let r: Double?      // read (legacy)
    let w: Double?      // write (legacy)
    let rb: Double?     // read bytes/s
    let wb: Double?     // write bytes/s

    var readBytesPerSec: Double? { rb ?? r }
    var writeBytesPerSec: Double? { wb ?? w }
}

// MARK: - Container

struct BeszelContainer: Codable, Identifiable {
    let n: String       // name
    let cpu: Double?    // CPU %
    let m: Double?      // Memory (MB)
    let b: [Double]?    // Bandwidth array
    let ns: Double?     // Network sent (legacy MB/s)
    let nr: Double?     // Network received (legacy MB/s)

    var id: String { n }
    var name: String { n }
    var cpuValue: Double { cpu ?? 0 }
    var mValue: Double { m ?? 0 }

    var bandwidthUpBytesPerSec: Double? {
        b?.first ?? ns.map { $0 * BESZEL_MB_TO_BYTES }
    }

    var bandwidthDownBytesPerSec: Double? {
        (b?.count ?? 0) > 1 ? b![1] : nr.map { $0 * BESZEL_MB_TO_BYTES }
    }
}

// MARK: - Container Records (static list)

enum BeszelContainerHealth: Int, Codable {
    case none = 0
    case starting = 1
    case healthy = 2
    case unhealthy = 3
}

struct BeszelContainerRecord: Codable, Identifiable {
    let id: String
    let collectionId: String?
    let collectionName: String?
    let name: String
    let cpu: Double?
    let memory: Double?
    let net: Double?
    let health: BeszelContainerHealth?
    let status: String?
    let image: String?
    let system: String?
    let updated: Int64?

    var cpuValue: Double { cpu ?? 0 }
    var memoryValue: Double { memory ?? 0 }
    var netValue: Double { net ?? 0 }
}

struct BeszelContainersResponse: Codable {
    let items: [BeszelContainerRecord]
    let totalItems: Int?
    let page: Int?
    let perPage: Int?
}

// MARK: - Container Stats (time series)

struct BeszelContainerStatsRecord: Codable, Identifiable {
    let id: String
    let system: String?
    let stats: [BeszelContainerStat]
    let created: String?
}

struct BeszelContainerStat: Codable, Identifiable {
    let n: String       // name
    let c: Double       // cpu
    let m: Double       // memory
    let ns: Double?     // net sent
    let nr: Double?     // net received

    var id: String { n }
    var name: String { n }
    var cpu: Double { c }
    var memory: Double { m }
    var netSent: Double { ns ?? 0 }
    var netReceived: Double { nr ?? 0 }
}

struct BeszelContainerStatsResponse: Codable {
    let items: [BeszelContainerStatsRecord]
    let totalItems: Int?
    let page: Int?
    let perPage: Int?
}

// MARK: - Network Interface

struct BeszelNetworkInterface {
    let uploadRateBytesPerSec: Double
    let downloadRateBytesPerSec: Double
    let uploadTotalBytes: Double?
    let downloadTotalBytes: Double?
}

struct BeszelRecordsResponse: Codable {
    let items: [BeszelSystemRecord]
    let totalItems: Int?
    let page: Int?
    let perPage: Int?
}

// MARK: - Auth

struct BeszelAuthResponse: Codable {
    let token: String
    let record: BeszelUserRecord?
}

struct BeszelUserRecord: Codable {
    let id: String
    let email: String?
    let username: String?
}

// MARK: - System Details (hardware info)

struct BeszelSystemDetails: Codable, Identifiable {
    let id: String
    let system: String?
    let hostname: String?
    let osName: String?
    let os: Int?
    let kernel: String?
    let arch: String?
    let cores: Int?
    let threads: Int?
    let memory: Int64?    // bytes
    let cpu: String?
    let podman: Bool?
    let updated: String?

    enum CodingKeys: String, CodingKey {
        case id, system, hostname
        case osName = "os_name"
        case os, kernel, arch, cores, threads, memory, cpu, podman, updated
    }
}

struct BeszelSystemDetailsResponse: Codable {
    let items: [BeszelSystemDetails]
    let totalItems: Int?
    let page: Int?
    let perPage: Int?
}

// MARK: - S.M.A.R.T. Devices

struct BeszelSmartAttribute: Decodable, Identifiable {
    var id: Int? = nil
    let n: String?        // name
    let v: Int?           // value
    let w: Int?           // worst
    let t: Int?           // threshold
    let rv: Int64?        // rawValue
    let rs: String?       // rawString

    var name: String { n ?? "" }
    var value: Int? { v }
    var worst: Int? { w }
    var threshold: Int? { t }
    var rawValue: Int64? { rv }
    var rawString: String? { rs }

    // Stable identity for ForEach
    var stableId: String { "\(id ?? 0)_\(name)" }

    // Custom decoder for robustness — handles numeric types flexibly
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try? c.decodeIfPresent(Int.self, forKey: .id)
        n = try? c.decodeIfPresent(String.self, forKey: .n)
        let decodedV = (try? c.decodeIfPresent(Int.self, forKey: .v))
            ?? (try? c.decodeIfPresent(Double.self, forKey: .v)).map { Int($0) }
        v = decodedV
        
        let decodedW = (try? c.decodeIfPresent(Int.self, forKey: .w))
            ?? (try? c.decodeIfPresent(Double.self, forKey: .w)).map { Int($0) }
        w = decodedW
        
        let decodedT = (try? c.decodeIfPresent(Int.self, forKey: .t))
            ?? (try? c.decodeIfPresent(Double.self, forKey: .t)).map { Int($0) }
        t = decodedT
        
        if let val = try? c.decodeIfPresent(Int64.self, forKey: .rv) {
            rv = val
        } else if let dbl = try? c.decodeIfPresent(Double.self, forKey: .rv) {
            rv = Int64(dbl)
        } else if let str = try? c.decodeIfPresent(String.self, forKey: .rv) {
            rv = Int64(str)
        } else {
            rv = nil
        }
        rs = try? c.decodeIfPresent(String.self, forKey: .rs)
    }

    private enum CodingKeys: String, CodingKey {
        case id, n, v, w, t, rv, rs
    }
}

struct BeszelSmartDevice: Decodable, Identifiable {
    let id: String
    let system: String?
    let name: String?       // device name
    let model: String?
    let capacity: Double?   // bytes (PocketBase sends as number)
    let state: String?      // status (PASSED/FAILING)
    let type: String?
    let hours: Int?
    let cycles: Int?
    let temp: Double?       // temperature celsius
    let updated: String?
    let attributes: [BeszelSmartAttribute]?

    var device: String { name ?? "Unknown" }
    var status: String { state ?? "Unknown" }
    var temperatureCelsius: Double? { temp }
    var capacityBytes: Int64 { Int64(capacity ?? 0) }

    private enum CodingKeys: String, CodingKey {
        case id, system, name, model, capacity, state, type, hours, cycles, temp, updated, attributes
    }

    private enum FallbackKeys: String, CodingKey {
        case device, status
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = (try? c.decode(String.self, forKey: .id))
            ?? (try? c.decode(Int.self, forKey: .id)).map(String.init)
            ?? UUID().uuidString
        system = try? c.decodeIfPresent(String.self, forKey: .system)
        
        let f = try decoder.container(keyedBy: FallbackKeys.self)
        
        // API might return 'device' instead of 'name'
        name = (try? c.decodeIfPresent(String.self, forKey: .name))
            ?? (try? f.decodeIfPresent(String.self, forKey: .device))
            
        model = try? c.decodeIfPresent(String.self, forKey: .model)
        capacity = Self.decodeDouble(from: c, forKey: .capacity)
        
        // API might return 'status' instead of 'state'
        state = (try? c.decodeIfPresent(String.self, forKey: .state))
            ?? (try? f.decodeIfPresent(String.self, forKey: .status))
            
        type = try? c.decodeIfPresent(String.self, forKey: .type)
        hours = Self.decodeInt(from: c, forKey: .hours)
        cycles = Self.decodeInt(from: c, forKey: .cycles)
        temp = Self.decodeDouble(from: c, forKey: .temp)
        updated = try? c.decodeIfPresent(String.self, forKey: .updated)
        
        if let attrs = try? c.decodeIfPresent([BeszelSmartAttribute].self, forKey: .attributes) {
            attributes = attrs
        } else if let attrsDict = try? c.decodeIfPresent([String: BeszelSmartAttribute].self, forKey: .attributes) {
            attributes = Array(attrsDict.values).sorted(by: { ($0.id ?? 0) < ($1.id ?? 0) })
        } else if let attrsString = try? c.decodeIfPresent(String.self, forKey: .attributes),
                  let data = attrsString.data(using: .utf8),
                  let decoded = try? JSONDecoder().decode([BeszelSmartAttribute].self, from: data) {
            attributes = decoded
        } else {
            attributes = nil
        }
    }

    private static func decodeDouble(from container: KeyedDecodingContainer<CodingKeys>, forKey key: CodingKeys) -> Double? {
        if let v = try? container.decodeIfPresent(Double.self, forKey: key) { return v }
        if let v = try? container.decodeIfPresent(Int.self, forKey: key) { return Double(v) }
        if let v = try? container.decodeIfPresent(Int64.self, forKey: key) { return Double(v) }
        if let v = try? container.decodeIfPresent(String.self, forKey: key) { return Double(v) }
        return nil
    }

    private static func decodeInt(from container: KeyedDecodingContainer<CodingKeys>, forKey key: CodingKeys) -> Int? {
        if let v = try? container.decodeIfPresent(Int.self, forKey: key) { return v }
        if let v = try? container.decodeIfPresent(Double.self, forKey: key) { return Int(v) }
        if let v = try? container.decodeIfPresent(String.self, forKey: key) { return Int(v) }
        return nil
    }
}

struct BeszelSmartDevicesResponse: Decodable {
    let items: [BeszelSmartDevice]
    let totalItems: Int?
    let page: Int?
    let perPage: Int?

    private enum CodingKeys: String, CodingKey {
        case items, totalItems, page, perPage
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        totalItems = try? container.decodeIfPresent(Int.self, forKey: .totalItems)
        page = try? container.decodeIfPresent(Int.self, forKey: .page)
        perPage = try? container.decodeIfPresent(Int.self, forKey: .perPage)

        var itemsContainer = try container.nestedUnkeyedContainer(forKey: .items)
        var decodedItems: [BeszelSmartDevice] = []
        while !itemsContainer.isAtEnd {
            do {
                let item = try itemsContainer.decode(BeszelSmartDevice.self)
                decodedItems.append(item)
            } catch {
                // Skip one element by decoding as Any (using JSONSerialization is overkill, just decode as dummy Decodable)
                _ = try? itemsContainer.decode(UnusedResilientDecodable.self)
                print("Beszel: Skipping malformed SMART device: \(error)")
            }
        }
        self.items = decodedItems
    }
}

private struct UnusedResilientDecodable: Decodable {
    init(from decoder: Decoder) throws {
        // Just consume the container without storing anything
        let _ = try? decoder.container(keyedBy: DynamicKey.self)
        // If it's an array, it might fail, but nestedUnkeyedContainer index increments anyway
    }
    
    private struct DynamicKey: CodingKey {
        var stringValue: String
        init?(stringValue: String) { self.stringValue = stringValue }
        var intValue: Int?
        init?(intValue: Int) { self.intValue = intValue; self.stringValue = "\(intValue)" }
    }
}
