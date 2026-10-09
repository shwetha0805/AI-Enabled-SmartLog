import os
import requests
import joblib

from sklearn.feature_extraction import DictVectorizer
from sklearn.ensemble import IsolationForest


ES_URL = (
    "https://100.94.231.105:9200/"
    "smartlog-windows/_search"
    "?size=200&sort=@timestamp:asc"
)

USERNAME = "elastic"
PASSWORD = os.getenv("SMARTLOG_ES_PASSWORD")

if not PASSWORD:
    raise RuntimeError(
        "SMARTLOG_ES_PASSWORD environment variable is not set."
    )


print("Connecting to Elasticsearch...")

response = requests.get(
    ES_URL,
    auth=(USERNAME, PASSWORD),
    verify=False,
    timeout=20
)

if response.status_code != 200:
    raise RuntimeError(
        f"Elasticsearch returned HTTP {response.status_code}: "
        f"{response.text}"
    )

data = response.json()

hits = data.get("hits", {}).get("hits", [])

print(f"Events received: {len(hits)}")

if len(hits) < 10:
    raise RuntimeError(
        "Not enough real Windows events for training. "
        "Need at least 10 events."
    )


records = []

for hit in hits:

    source = hit.get("_source", {})

    winlog = source.get("winlog", {})
    event = source.get("event", {})
    event_data = winlog.get("event_data", {})

    event_code = str(
        event.get(
            "code",
            winlog.get("event_id", "UNKNOWN")
        )
    )

    event_type = str(
        event.get(
            "action",
            "UNKNOWN"
        )
    )

    outcome = str(
        event.get(
            "outcome",
            "UNKNOWN"
        )
    )

    channel = str(
        winlog.get(
            "channel",
            "UNKNOWN"
        )
    )

    severity = str(
        source.get(
            "severity",
            "LOW"
        )
    )

    logon_type = str(
        event_data.get(
            "LogonType",
            "UNKNOWN"
        )
    )

    records.append({
        "event_code": event_code,
        "event_type": event_type,
        "outcome": outcome,
        "channel": channel,
        "severity": severity,
        "logon_type": logon_type
    })


print("Preparing ML features...")

vectorizer = DictVectorizer(
    sparse=True
)

X = vectorizer.fit_transform(records)

print(f"Training samples: {X.shape[0]}")
print(f"Features: {X.shape[1]}")


model = IsolationForest(
    n_estimators=200,
    contamination="auto",
    random_state=42
)

model.fit(X)


os.makedirs("model", exist_ok=True)

joblib.dump(
    model,
    "model/isolation_forest.joblib"
)

joblib.dump(
    vectorizer,
    "model/vectorizer.joblib"
)


print()
print("====================================")
print("ML MODEL TRAINING COMPLETE")
print("====================================")
print("Model saved:")
print("model/isolation_forest.joblib")
print()
print("Vectorizer saved:")
print("model/vectorizer.joblib")