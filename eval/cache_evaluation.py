import httpx
import statistics
import time
import os

BASE_URL = "http://localhost:8080"
ENDPOINT = "/api/recipes"

TOKEN = os.environ.get("BENCHMARK_TOKEN")

HEADERS = {"Authorization": f"Bearer {TOKEN}"}
RUNS = 50

def measure_response_times(label, evict_before_each=False):
    times = []
    for _ in range(RUNS):
        if evict_before_each:
            r = httpx.delete(f"{BASE_URL}/api/recipes/cache/evict", headers=HEADERS)
            assert r.status_code == 204, f"Evict failed: {r.status_code}"

        start = time.perf_counter()
        r = httpx.get(f"{BASE_URL}{ENDPOINT}", headers=HEADERS)
        end = time.perf_counter()
        assert r.status_code == 200, f"Got {r.status_code}"
        times.append((end - start) * 1000)
        time.sleep(0.05)

    print(f"\n--- {label} ---")
    print(f"Samples:  {len(times)}")
    print(f"Mean:     {statistics.mean(times):.2f}ms")
    print(f"Median:   {statistics.median(times):.2f}ms")
    print(f"p95:      {sorted(times)[int(0.95 * len(times))]:.2f}ms")
    print(f"p99:      {sorted(times)[int(0.99 * len(times))]:.2f}ms")
    print(f"Min:      {min(times):.2f}ms")
    print(f"Max:      {max(times):.2f}ms")
    return times

print("=== COLD CACHE (first request populates cache) ===")
print("Make sure you just restarted the server before running this.")
input("Press Enter when ready...")


# First request — cold, goes to DB
cold_times = measure_response_times("Cold Cache (DB hit)", evict_before_each=True)


print("\n=== WARM CACHE (subsequent requests served from memory) ===")
input("Press Enter when ready...")

# Subsequent requests — served from ConcurrentHashMap
warm_times = measure_response_times("Warm Cache (memory hit)", evict_before_each=False)


# Summary
mean_cold = statistics.mean(cold_times)
mean_warm = statistics.mean(warm_times)
reduction = ((mean_cold - mean_warm) / mean_cold) * 100

print(f"\n=== SUMMARY ===")
print(f"Mean cold: {mean_cold:.2f}ms")
print(f"Mean warm: {mean_warm:.2f}ms")
print(f"Reduction: {reduction:.1f}%")
