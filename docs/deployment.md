# Deployment architecture

The BioStudies Index Service is deployed in Kubernetes using separate writer and reader workloads.

The writer pod is responsible for building and updating the Lucene index. It keeps its main index
data on local persistent storage and uses a snapshot sidecar to synchronize published index
snapshots to shared NFS storage. Reader pods then consume the shared index from that storage.

## Kubernetes layout

```mermaid
flowchart LR
    subgraph DevNamespace["biostudies-index-service-dev namespace"]
        subgraph WriterPod["indexer-writer StatefulSet (1 replica)"]
            W["Indexer container\n(role=writer)\nBuilds and updates Lucene index"]
            S["Snapshot sidecar\n(rsync loop)\nCopies index snapshots"]
            L["Local index storage\nPVC: index-storage\nReadWriteOnce"]
            E["EFO data volume\n(emptyDir)"]
            C["Security config\n(secret)"]

            W --> L
            S --- L
            W --> E
            W --> C
            S --> SH
        end

        subgraph SharedPVC["shared-indexes-pvc\nNFS-backed PVC\nReadWriteMany"]
            SH["Shared index location\n/current"]
        end

        subgraph ReaderPods["indexer-reader StatefulSet (2 replicas)"]
            R1["Reader pod\n(role=reader)\nReads shared Lucene index"]
            R2["Reader pod\n(role=reader)\nReads shared Lucene index"]
        end
    end

    SH --> R1
    SH --> R2
```

## Roles

### Writer

The writer pod builds the index and maintains the authoritative local copy. A sidecar periodically
synchronizes snapshots to shared storage.

### Shared storage

The shared NFS volume provides a common read location for published index snapshots.

### Reader

Reader pods mount the shared index and use it for read-oriented access to the published Lucene data.