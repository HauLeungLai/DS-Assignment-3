# Assignment 3
## Overview 
This project is about implements the **Poxos Consesus Algorithm** in Java, simulating the election of a Council President **Adelaide Suburbs Council**.
The system consists of 9 council members M1-M9, each acting as **Proposer**, **Acceptor**, and **Learner**, and communicate via TCP sockets. 

The program contains 3 scenarios test:
- A single president is elected, even if multiple proposals are made concurrently.
- The system reaches consensus despite network latency or membere failures.
- All running non-faulty nodes learn the same final decision.

---
## Project Structure
```
|— council/
|    |— CouncilMember.java  # Main entry point
|    |— TcpTransport.java   # TCP communication layer
|    |— NetworkConfig.java  # Config loader for member host:port
|    |— MessageType.java    # Message type enum
|    |— MessageCodec.java   # Serialization / deserialization
|    |— PaxosState.java     # Acceptor state
|    |— Proposer.java       # Proposer role logic
|    |— Acceptor.java       # Acceptor role logic
|    |— Learner.java        # Learner role logic
|— network.config           # Member ID to host:port mapping
|— run_tests.sh             # Automated test script (Scenario 1–3)
|— README.md
```
---
## Compile
```bash
javac -d out $(find src -name "*.java")
```

---
## How to run
If you wish to run it in manual way (more complex)
You need to start nine terminals or background processes, one per member:
```bash
java -cp out council.CouncilMember M1
java -cp out council.CouncilMember M2
java -cp out council.CouncilMember M3
...
java -cp out council.CouncilMember M9
```
What you should see is: 
xx listening on 900x
xx ready. Type a candidate id to propose (e.g., M5).

Types a candidate ID in any node(e.g., M4 proposes M5) to inititate Paxos.
All non-faulty nodes should print:
CONSENSUS: M5 has been elected Council President!

---
The more recommand way to test it is test with **run_tests.sh**
```bash
chmod +x run_tests.sh
./run_tests.sh
```
After testing 3 scenario, it should be all pass and record each member's log in "logs" 

---
## Configuration
network.config defines the node address mapping:
```bash
M1,localhost,9001
M2,localhost,9002
M3,localhost,9003
M4,localhost,9004
M5,localhost,9005
M6,localhost,9006
M7,localhost,9007
M8,localhost,9008
M9,localhost,9009
```
This file must be located in the same directory as the run_tests.sh script.

