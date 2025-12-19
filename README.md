# README
## Overview

The NFC Tag Writer app is an Android application designed to interact with two types of NFC tags:
- **NTAG424 DNA** - Advanced NFC tags with AES-128 encryption
- **NTAG21X** - Standard NFC tags (NTAG213/215/216)

The app provides comprehensive functionality for reading, writing, and configuring both tag types through an intuitive user interface.

---

## NTAG424 DNA

### Supported Tags
- NTAG424 DNA (with AES-128 encryption support)

### Key Features

#### 1. **Set Password (Key)**
- **Function**: Sets a new 16-byte AES-128 encryption key on the tag
- **Input**: 32-character hexadecimal password (16 bytes)
- **Default Key**: Uses factory default key (Key 00 - all zeros) for initial authentication
- **Process**: 
  - Authenticates with factory key
  - Changes Key 00 to the new password
  - Converts hex string to 16-byte AES-128 key
- **Status**: Provides success/error feedback

#### 2. **Configure CC File**
- **Function**: Configures the Capability Container (CC) file
- **Status**: UI implemented, functionality ready for extension
- **Purpose**: Sets up tag capabilities and memory structure

#### 3. **Configure File Access**
- **Function**: Configures file access permissions with authentication
- **Settings**:
  - Write access: Requires Key 0 authentication
  - Read access: Open to everyone (no authentication needed)
- **Process**:
  - Authenticates with current password
  - Retrieves current file settings
  - Modifies access permissions
  - Applies new settings to NDEF file
- **Result**: Write operations require password, read operations are public

#### 4. **Write Data with Authentication**
- **Function**: Writes data to the tag after authenticating with password
- **Input**: 
  - Data string (URL or text)
  - Password (32 hex characters)
- **Process**:
  - Authenticates with provided password using AES-128
  - Creates NDEF message from input data
  - Writes to NDEF file (file 0x01)
- **Security**: Requires successful authentication before writing

#### 5. **Read Data without Authentication**
- **Function**: Reads data from the tag without requiring authentication
- **Process**:
  - Reads NDEF file directly
  - Parses NDEF records (URI or text)
  - Displays content in read-only field
- **Access**: Works without password (if file access is configured correctly)

### Technical Implementation
- **Library**: Uses [ntag424-java](https://github.com/johnnyb/ntag424-java) library
- **Protocol**: ISO-DEP (ISO/IEC 14443-4)
- **Encryption**: AES-128 encryption mode
- **Authentication**: EV2 authentication protocol
- **File System**: Uses DESFire file structure

### UI Components
- Password input field (32 hex characters)
- Data to write input field
- Status message display (color-coded: success/error/info)
- Read result display area
- Action buttons with icons:
  - Set Password (Orange)
  - Configure CC File (Blue)
  - Configure File Access (Purple)
  - Read (Blue)
  - Write (Green)

---

## NTAG21X

### Supported Tags
- NTAG213 (180 bytes user memory)
- NTAG215 (540 bytes user memory)
- NTAG216 (924 bytes user memory)

### Key Features

#### 1. **Set Password**
- **Function**: Sets a 4-byte password on the tag
- **Input Format**: 
  - 4 digits (0-9): e.g., "5678"
  - 8 hex characters: e.g., "5678ABCD"
- **Storage**: Password stored at memory page 0x2B
- **Process**:
  - Converts input to 4-byte array
  - Writes password to password page
  - Verifies write success
- **Status**: Provides success/error feedback

#### 2. **Write Data with Authentication**
- **Function**: Writes data to the tag after authenticating with password
- **Input**:
  - Data string (URL or text)
  - Password (4 digits or 8 hex characters)
- **Process**:
  - Authenticates using PWD_AUTH command (0x1B)
  - Verifies PACK (Password Acknowledge) response
  - Creates NDEF message (URI or text record)
  - Writes NDEF message to tag
  - Supports both Ndef and NfcA write methods
- **Security**: Requires successful password authentication
- **Format**: Automatically detects URLs and creates appropriate NDEF records

#### 3. **Read Data without Authentication**
- **Function**: Reads data from the tag without requiring password
- **Process**:
  - Attempts to read using Ndef technology first
  - Falls back to direct NfcA page reading if needed
  - Parses NDEF records (URI or text)
  - Extracts and displays content
- **Access**: No authentication required
- **Output**: Displays read content in scrollable text field

#### 4. **Read Tag Information**
- **Function**: Reads tag identification and capability information
- **Data Retrieved**:
  - UID (Unique Identifier)
  - Capability Container
  - Memory pages 0-3 (tag metadata)
- **Format**: Displays hex dump of tag information pages
- **Purpose**: Useful for debugging and tag identification

### Technical Implementation
- **Technology**: NFC Type 2 Tag (ISO/IEC 14443 Type A)
- **Protocol**: Uses `NfcA` technology for low-level communication
- **Authentication**: PWD_AUTH command (0x1B)
- **Commands**:
  - READ (0x30): Read memory pages
  - WRITE (0xA2): Write memory pages
  - PWD_AUTH (0x1B): Password authentication
- **Memory Structure**:
  - Pages 0-3: UID and Capability Container
  - Page 4+: User data area (NDEF messages)
  - Page 0x2A: AUTH0 (protection start page)
  - Page 0x2B: Password storage
  - Page 0x2C: PACK (password acknowledge)

### UI Components
- Password input field (4 digits or 8 hex characters)
- Text to write input field
- Read result display area (multi-line, scrollable)
- Status message display (color-coded: success/error/info)
- Password protection mode toggle (UI only, for reference)
- Action buttons with icons:
  - Set Password (Orange)
  - Read NFC Tag (Blue)
  - Write NFC Tag (Green)
  - Read Tag Information (Purple)

---

## Common Features (Both Parts)

### User Interface
- **Material Design 3**: Modern, clean interface
- **Color-coded Status Messages**:
  - Green: Success messages
  - Red: Error messages
  - Blue: Information messages
- **Processing State**: Buttons disabled during operations
- **Scrollable Content**: All screens support vertical scrolling
- **Real-time Feedback**: Immediate status updates for all operations

### NFC Tag Detection
- **Automatic Detection**: Tags detected when scanned
- **Foreground Dispatch**: App receives NFC intents when active
- **Tag Validation**: Verifies tag compatibility before operations
- **Error Handling**: Comprehensive error messages for common issues

### Navigation
- **Bottom Navigation Bar**: Easy switching between NTAG424, NTAG21X, and Settings
- **Tab-based Interface**: Three main sections
- **State Preservation**: Navigation state maintained across tab switches

### Error Handling
- **Input Validation**: Validates password format and data before operations
- **Tag Compatibility**: Checks tag type before attempting operations
- **Connection Errors**: Handles NFC communication failures gracefully
- **User Feedback**: Clear error messages with actionable information

---

## Technical Requirements

### Android Requirements
- **Minimum SDK**: 24 (Android 7.0)
- **Target SDK**: 36 (Android 15)
- **NFC Hardware**: Required
- **Permissions**: NFC permission

### Dependencies
- **NTAG424**: Requires ntag424-java library JAR file in `app/libs/`
- **NTAG21X**: Uses native Android NFC API (no external dependencies)
- **Compose**: Jetpack Compose for UI
- **Navigation**: Navigation Compose for screen navigation
- **Lifecycle**: Lifecycle-aware components for state management

### Setup Instructions
1. **NTAG424**: Add ntag424-java JAR to `app/libs/` directory
2. **NTAG21X**: No additional setup required
3. **NFC**: Enable NFC on device
4. **Permissions**: NFC permission automatically granted

---

## Usage Workflow

### NTAG424 Workflow
1. Navigate to NTAG424 tab
2. Scan NTAG424 tag
3. Enter 32-character hex password
4. (Optional) Set password using factory key
5. (Optional) Configure file access permissions
6. Enter data to write
7. Write data (requires authentication)
8. Read data (no authentication needed)

### NTAG21X Workflow
1. Navigate to NTAG21X tab
2. Scan NTAG21X tag
3. Enter 4-digit or 8-hex-character password
4. (Optional) Set password on tag
5. Enter data to write
6. Write data (requires authentication)
7. Read data (no authentication needed)
8. (Optional) Read tag information

---

## Security Considerations

### NTAG424
- **Encryption**: AES-128 encryption for all authenticated operations
- **Key Management**: Factory key (all zeros) used for initial setup
- **Access Control**: Configurable read/write permissions
- **Authentication**: Required for all write operations

### NTAG21X
- **Password Protection**: 4-byte password protection
- **Authentication**: Required for write operations
- **Read Access**: Public read access (configurable)
- **Memory Protection**: Page-level protection available

---

## Differences Between NTAG424 and NTAG21X

| Feature | NTAG424 | NTAG21X |
|---------|---------|---------|
| **Protocol** | ISO-DEP | NFC Type 2 Tag |
| **Technology** | IsoDep | NfcA |
| **Password Size** | 16 bytes (32 hex) | 4 bytes (4 digits/8 hex) |
| **Encryption** | AES-128 | None (password only) |
| **Library** | External (ntag424-java) | Native Android API |
| **Memory** | File-based | Page-based |
| **Authentication** | AES encryption | PWD_AUTH command |
| **Complexity** | High (enterprise-grade) | Low (standard NFC) |
| **Use Case** | Secure applications | General purpose |

---

## Future Enhancements

### Potential Additions
- **NTAG424**:
  - LRP (Leakage-Resistant Primitive) encryption mode support
  - Secure Dynamic Messaging (SDM) configuration
  - Key diversification
  - SUN message generation

- **NTAG21X**:
  - AUTH0 configuration (protection start page)
  - PROT configuration (protection level)
  - Memory page visualization
  - Batch operations

- **General**:
  - Tag history/logging
  - Export/import configurations
  - Multiple tag profiles
  - Advanced error recovery

---

## Conclusion

The NFC Tag Writer app provides comprehensive functionality for both enterprise-grade NTAG424 DNA tags and standard NTAG21X tags. With intuitive interfaces, robust error handling, and clear user feedback, it serves as a complete solution for NFC tag management and data operations on Android devices.
