import Foundation

// MARK: - Sheet Navigation Types

enum ExtraMetricType: String, Identifiable, CaseIterable {
    case temperature, load, network, diskIO, battery, swap
    var id: String { rawValue }
}

enum ResourceMetricType: String, Identifiable {
    case cpu, memory
    var id: String { rawValue }
}

enum GpuMetricType: Identifiable, Equatable {
    case usage(gpuKey: String)
    case power(gpuKey: String)
    case vram(gpuKey: String)

    var id: String {
        switch self {
        case .usage(let key): return "usage_\(key)"
        case .power(let key): return "power_\(key)"
        case .vram(let key): return "vram_\(key)"
        }
    }

    var gpuKey: String {
        switch self {
        case .usage(let key), .power(let key), .vram(let key): return key
        }
    }
}

enum DockerMetricType: String, Identifiable {
    case cpu, memory, network
    var id: String { rawValue }
}

// MARK: - UI Helper Types

struct DiskFsUsage: Identifiable {
    let key: String
    let label: String
    let usedGb: Double
    let totalGb: Double
    let isRoot: Bool
    var id: String { key }
    var percent: Double {
        totalGb > 0 ? min(usedGb / totalGb * 100, 100) : 0
    }
}

struct DockerMetricSummary {
    let cpuPercent: Double
    let memoryUsedMb: Double
    let uploadRate: Double?
    let downloadRate: Double?
}

struct BandwidthPoint {
    let rx: Double
    let tx: Double
}

// MARK: - Detail UI Model

struct BeszelSystemDetailUiModel {
    let system: BeszelSystem
    let systemDetails: BeszelSystemDetails?
    let records: [BeszelSystemRecord]
    let smartDevices: [BeszelSmartDevice]

    // Last N records for history (oldest first)
    var historyRecords: [BeszelRecordStats] {
        Array(records.sorted { ($0.created ?? "") < ($1.created ?? "") }.suffix(30).map(\.stats))
    }

    var cpuHistory: [Double] {
        historyRecords.map(\.cpuValue)
    }

    var memoryHistory: [Double] {
        historyRecords.map(\.mpValue)
    }

    var diskHistory: [Double] {
        historyRecords.map(\.dpValue)
    }

    var latestStats: BeszelRecordStats? {
        records.sorted { ($0.created ?? "") > ($1.created ?? "") }.first?.stats
    }

    // External filesystems from latest stats
    var externalFilesystems: [DiskFsUsage] {
        guard let efs = latestStats?.efs else { return [] }
        return efs.compactMap { label, entry in
            guard let d = entry.d, let du = entry.du else { return nil }
            return DiskFsUsage(key: label, label: label, usedGb: du, totalGb: d, isRoot: false)
        }.sorted { $0.label < $1.label }
    }

    // Docker aggregation
    var dockerSummary: DockerMetricSummary? {
        guard let containers = latestStats?.dc, !containers.isEmpty else { return nil }
        let totalCpu = containers.reduce(0.0) { $0 + $1.cpuValue }
        let totalMem = containers.reduce(0.0) { $0 + $1.mValue }
        let totalUp = containers.compactMap(\.bandwidthUpBytesPerSec).reduce(0.0, +)
        let totalDown = containers.compactMap(\.bandwidthDownBytesPerSec).reduce(0.0, +)
        let hasNetwork = containers.contains { $0.bandwidthUpBytesPerSec != nil }
        return DockerMetricSummary(
            cpuPercent: totalCpu,
            memoryUsedMb: totalMem,
            uploadRate: hasNetwork ? totalUp : nil,
            downloadRate: hasNetwork ? totalDown : nil
        )
    }

    var hasGpu: Bool { !(latestStats?.gpuEntries.isEmpty ?? true) }
    var hasSmartDevices: Bool { !smartDevices.isEmpty }
    var hasBattery: Bool { latestStats?.batteryLevel != nil }
    var hasSwap: Bool { latestStats?.swapTotalGb != nil }
    var hasLoadAvg: Bool { !(latestStats?.loadAvgValues.isEmpty ?? true) }
    var hasTemperature: Bool { latestStats?.maxTempCelsius != nil }
    var hasNetworkInterfaces: Bool { !(latestStats?.networkInterfaces.isEmpty ?? true) }
    var hasDiskIO: Bool { latestStats?.diskReadBytesPerSec != nil }
    var hasCpuBreakdown: Bool { !(latestStats?.cpuBreakdownValues.isEmpty ?? true) }
    var hasPerCoreCpu: Bool { !(latestStats?.cpuCoreUsageValues.isEmpty ?? true) }
}
