# Multithreaded File Transfer

A Java client-server application for **parallel directory transfer over TCP sockets**, featuring configurable worker threads, resumable downloads, persistent progress tracking, and a JavaFX graphical interface.

Developed as a university project for the **Concurrent Programming (KOPR)** course at Pavol Jozef Šafárik University in Košice, Slovakia.

## Features

- **Multithreaded file transfer** – transfers multiple files concurrently using a configurable number of TCP connections.
- **Client-server architecture** – separates file distribution and transfer management between server and client applications.
- **Resumable downloads** – supports continuing interrupted transfers from previously saved byte offsets.
- **Persistent progress tracking** – stores transfer progress in a local `.progress.dat` file.
- **JavaFX graphical interface** – provides controls for starting transfers and selecting the number of workers.
- **Real-time progress monitoring** – displays the number of completed files and the total amount of downloaded data.
- **Recursive directory transfer** – supports files located in nested directories.
- **Thread synchronization** – coordinates worker completion using `CountDownLatch`.
- **Thread-safe task distribution** – uses `LinkedBlockingQueue` to distribute files among server workers.

## Technologies

| Technology | Purpose |
|---|---|
| Java 18 | Core application development |
| JavaFX 23 | Desktop graphical user interface |
| TCP Sockets | Client-server network communication |
| ExecutorService | Multithreaded task execution |
| BlockingQueue | Thread-safe file task distribution |
| CountDownLatch | Worker synchronization |
| ConcurrentHashMap | Thread-safe progress tracking |
| RandomAccessFile | Writing files from specific byte offsets |
| Maven | Build and dependency management |
| JUnit 5 | Unit testing |

## Architecture

The application consists of two components: a **server** that provides files and a **client** that downloads them.

### Server

The server:

1. Recursively scans the configured source directory.
2. Collects the relative paths and sizes of available files.
3. Accepts client requests through a management TCP connection.
4. Sends the file list to the client.
5. Creates the requested number of worker sockets.
6. Distributes files among workers using a shared `LinkedBlockingQueue`.
7. Executes file-transfer tasks using a fixed thread pool.

The management connection uses TCP port `5000`. Worker ports are dynamically assigned by the operating system.

### Client

The client:

1. Connects to the server and requests the available file list.
2. Loads previously saved download progress.
3. Creates a configurable number of worker tasks.
4. Establishes separate TCP connections for file transfers.
5. Writes incoming data to the destination directory.
6. Updates the JavaFX progress indicators.
7. Coordinates worker completion using `CountDownLatch`.

The interface is updated approximately every 500 milliseconds using `ScheduledExecutorService` and `Platform.runLater()`.

## Transfer Workflow

```text
                 SERVER
          Source Directory
                 |
        Recursive File Scan
                 |
          Management Socket
             Port 5000
                 |
       File List + Worker Ports
                 |
          Shared Task Queue
                 |
       +---------+---------+
       |         |         |
    Worker 1  Worker 2  Worker N
       |         |         |
       +---------+---------+
                 |
          TCP Connections
                 |
                CLIENT
                 |
         FileReceiveTask
                 |
       RandomAccessFile
                 |
       Destination Directory
          ./downloaded
                 |
         Progress Tracking
                 |
          JavaFX Interface
```

Each worker maintains its own TCP connection and processes files assigned through the server's shared task queue.

## Resumable File Transfers

The application implements a recovery mechanism that allows interrupted downloads to continue from previously saved positions.

### How It Works

1. **Track downloaded bytes:** The client maintains a thread-safe map containing the downloaded byte count for each file.
2. **Save progress:** Transfer information is periodically serialized into `.progress.dat`.
3. **Restore progress:** When a new transfer starts, the client loads the saved state and compares it with the server's file list and local files.
4. **Request remaining data:** The client sends a `FileRequest` containing the saved offset and the remaining number of bytes.
5. **Continue writing:** `RandomAccessFile.seek()` positions the file pointer at the saved offset so the client can append the missing data.

### Implementation Details

| Parameter | Value |
|---|---|
| Transfer buffer | 16 KB |
| Progress save interval | Every 3,000 received blocks |
| Additional progress save | After processing a file |
| Progress storage | `.progress.dat` |
| Destination directory | `./downloaded` |
| Default worker count | 4 |

Progress is saved periodically rather than after every received block. Consequently, an unexpected shutdown may require some recently downloaded data to be transferred again.

Recovery also depends on the integrity of the saved progress file. The implementation is intended for educational use and does not guarantee recovery from every possible failure scenario.

## Graphical User Interface

The JavaFX client provides a simple desktop interface with:

- **Worker count input** – specifies the number of parallel transfer connections.
- **Start / Resume button** – initiates or continues file downloading.
- **Status label** – displays connection and transfer status.
- **File progress bar** – shows the number of completed files.
- **Data progress bar** – displays downloaded data relative to the total size.

The default number of workers is **4**.

## Project Structure

```text
src/
├── main/
│   └── java/sk/upjs/kopr/file_copy/
│       ├── FileInfo.java
│       ├── FileRequest.java
│       ├── client/
│       │   ├── ClientMain.java
│       │   ├── MainLauncher.java
│       │   ├── FileReceiveTask.java
│       │   └── ProgressInfo.java
│       └── server/
│           ├── Server.java
│           └── FileSendTask.java
└── test/
    └── java/sk/upjs/kopr/file_copy/client/
        └── ProgressInfoTest.java

pom.xml
```

### Main Classes

| Class | Responsibility |
|---|---|
| `Server` | Directory scanning, management connections, worker initialization and task distribution |
| `FileSendTask` | Server-side file transfer worker |
| `ClientMain` | JavaFX interface and client-side transfer coordination |
| `MainLauncher` | JavaFX application entry point |
| `FileReceiveTask` | File reception, partial downloads and progress updates |
| `ProgressInfo` | Persistent transfer state and progress calculations |
| `FileInfo` | File metadata |
| `FileRequest` | Requested file offset and remaining byte count |

## Getting Started

### Prerequisites

- Java Development Kit compatible with JavaFX 23 (JDK 23 recommended)
- Apache Maven
- IntelliJ IDEA or another Java IDE (optional)

### 1. Clone the Repository

```bash
git clone https://github.com/vladyslava-koliesnikova/multithreaded-file-transfer.git
cd multithreaded-file-transfer
```

### 2. Configure the Server

Open:

`src/main/java/sk/upjs/kopr/file_copy/server/Server.java`

Change the source directory to the folder you want to transfer:

```java
public static final File SOURCE_DIRECTORY =
        new File("D:/test_filecopy");
```

Make sure the directory exists before starting the server.

### 3. Start the Server

Open the project in IntelliJ IDEA and run the `main()` method in:

`sk.upjs.kopr.file_copy.server.Server`

The server scans the configured directory and listens on TCP port `5000`.

### 4. Start the Client

Open a separate terminal in the project directory and run:

```bash
mvn javafx:run
```

The Maven configuration uses `MainLauncher` as the JavaFX entry point.

The client connects to `localhost` by default.

### 5. Transfer Files

1. Start the server.
2. Launch the JavaFX client.
3. Enter the desired number of workers (default: `4`).
4. Click **Štart / Pokračovať** (Start / Resume).
5. Monitor the transfer progress.
6. Access the downloaded files in the `downloaded` directory.

### Running on Different Computers

To connect to a remote server, change the following value in `ClientMain.java`:

```java
private static final String SERVER_HOST = "localhost";
```

Replace `localhost` with the server's IP address.

The server's management port and dynamically assigned worker ports must be reachable from the client.

## Concurrency and Synchronization

The project demonstrates several Java concurrency mechanisms:

**ExecutorService**

Fixed thread pools execute transfer tasks on both the client and server.

**LinkedBlockingQueue**

The server maintains a shared, thread-safe queue of files awaiting transfer.

**CountDownLatch**

The client waits for all worker tasks to finish before completing the transfer session.

**ConcurrentHashMap**

Download progress is tracked in a thread-safe data structure shared between workers.

**ScheduledExecutorService**

A scheduled task periodically refreshes the progress indicators in the JavaFX interface.

## Limitations

This is an educational implementation with several limitations:

- Source and destination paths are configured directly in the source code.
- The client connects to `localhost` by default.
- Files are transferred from the server to the client only.
- Transfer progress is saved periodically, so some data may need to be retransmitted after an unexpected shutdown.
- The progress file is not written atomically and may become corrupted if interrupted during saving.
- The GUI does not reliably distinguish every failed transfer from successful completion.
- File integrity verification using checksums is not implemented.

## Academic Context and Acknowledgments

This project was developed as part of the **Concurrent Programming (KOPR)** course at the Faculty of Science, Pavol Jozef Šafárik University in Košice.

The implementation is based on the university-provided [file_copy starter project](https://gitlab.science.upjs.sk/kopr/file_copy).

The assignment focused on extending the starter project with concurrent directory transfer, TCP communication, worker synchronization, progress tracking, interruption handling, and a graphical client interface.

The project demonstrates practical experience with **Java concurrency, socket programming, client-server architecture, and desktop application development**.
