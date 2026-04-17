import Foundation
import CryptoKit
import CommonCrypto

// MARK: - Backup Crypto

/// Handles AES-256-GCM encryption/decryption with PBKDF2-derived keys
/// for .homelab backup files.
///
/// Binary format:
/// ```
/// [4 bytes: "HLAB"] [1 byte: format version] [16 bytes: salt] [12 bytes: nonce] [N bytes: ciphertext + GCM tag]
/// ```
enum BackupCrypto {

    // MARK: - Constants

    static let magic: [UInt8] = [0x48, 0x4C, 0x41, 0x42] // "HLAB"
    static let formatVersion: UInt8 = 1
    static let headerSize = 4 + 1 + 16 + 12 // magic + version + salt + nonce = 33 bytes
    private static let saltLength = 16
    private static let nonceLength = 12
    private static let pbkdf2Iterations = 600_000
    private static let keyLength = 32 // 256 bits

    // MARK: - Errors

    enum CryptoError: LocalizedError {
        case invalidFileFormat
        case unsupportedVersion
        case decryptionFailed
        case keyDerivationFailed

        var errorDescription: String? {
            switch self {
            case .invalidFileFormat:   return "Invalid backup file format. The file may be corrupted."
            case .unsupportedVersion:  return "Unsupported backup file version."
            case .decryptionFailed:    return "Decryption failed. Check your password and verify the file is not corrupted."
            case .keyDerivationFailed: return "Key derivation failed."
            }
        }
    }

    // MARK: - Public API

    /// Encrypts plaintext data with a password and returns the full .homelab binary payload.
    static func encrypt(data: Data, password: String) throws -> Data {
        let salt = randomBytes(count: saltLength)
        let key = try deriveKey(password: password, salt: salt)
        let nonce = try AES.GCM.Nonce(data: randomBytes(count: nonceLength))

        let sealed = try AES.GCM.seal(data, using: key, nonce: nonce)
        guard let combined = sealed.combined else {
            throw CryptoError.decryptionFailed
        }
        // combined = nonce + ciphertext + tag, but we store nonce separately
        // So extract ciphertext+tag (skip the 12-byte nonce that CryptoKit prepends)
        let ciphertextAndTag = combined.dropFirst(nonceLength)

        var output = Data()
        output.append(contentsOf: magic)
        output.append(formatVersion)
        output.append(contentsOf: salt)
        output.append(contentsOf: nonce.withUnsafeBytes { Array($0) })
        output.append(ciphertextAndTag)
        return output
    }

    /// Decrypts a .homelab binary payload with a password and returns the plaintext data.
    static func decrypt(data: Data, password: String) throws -> Data {
        guard data.count > headerSize else {
            throw CryptoError.invalidFileFormat
        }

        // Validate magic
        let fileMagic = Array(data.prefix(4))
        guard fileMagic == magic else {
            throw CryptoError.invalidFileFormat
        }

        // Validate version
        let version = data[4]
        guard version == formatVersion else {
            throw CryptoError.unsupportedVersion
        }

        // Extract components
        let salt = Data(data[5..<21])
        let nonceData = Data(data[21..<33])
        let ciphertextAndTag = Data(data[33...])

        let key = try deriveKey(password: password, salt: Array(salt))
        // Reconstruct the combined box that CryptoKit expects: nonce + ciphertext + tag
        var combined = Data(nonceData)
        combined.append(ciphertextAndTag)

        let sealedBox = try AES.GCM.SealedBox(combined: combined)

        do {
            return try AES.GCM.open(sealedBox, using: key)
        } catch {
            throw CryptoError.decryptionFailed
        }
    }

    // MARK: - Private

    private static func deriveKey(password: String, salt: [UInt8]) throws -> SymmetricKey {
        guard let passwordData = password.data(using: .utf8) else {
            throw CryptoError.keyDerivationFailed
        }

        // PBKDF2 with SHA-256
        var derivedKey = [UInt8](repeating: 0, count: keyLength)
        let status = derivedKey.withUnsafeMutableBytes { derivedKeyBytes in
            passwordData.withUnsafeBytes { passwordBytes in
                CCKeyDerivationPBKDF(
                    CCPBKDFAlgorithm(kCCPBKDF2),
                    passwordBytes.baseAddress?.assumingMemoryBound(to: Int8.self),
                    passwordData.count,
                    salt,
                    salt.count,
                    CCPseudoRandomAlgorithm(kCCPRFHmacAlgSHA256),
                    UInt32(pbkdf2Iterations),
                    derivedKeyBytes.baseAddress?.assumingMemoryBound(to: UInt8.self),
                    keyLength
                )
            }
        }

        guard status == kCCSuccess else {
            throw CryptoError.keyDerivationFailed
        }

        return SymmetricKey(data: derivedKey)
    }

    private static func randomBytes(count: Int) -> [UInt8] {
        var bytes = [UInt8](repeating: 0, count: count)
        _ = SecRandomCopyBytes(kSecRandomDefault, count, &bytes)
        return bytes
    }
}
