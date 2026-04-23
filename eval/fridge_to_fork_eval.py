import argparse
import json
import requests
import anthropic
from datetime import datetime
from dataclasses import dataclass, asdict

# ─── Config ───────────────────────────────────────────────
API_BASE = "http://localhost:8080"
EVAL_MODEL = "claude-haiku-4-5-20251001"
N_TEST_CASES = 15

client = anthropic.Anthropic()


# ─── Data Classes ─────────────────────────────────────────
@dataclass
class TestCase:
    available_ingredients: str
    expected_top_match: str
    difficulty: str  # easy | medium | hard


@dataclass
class EvalResult:
    query: str
    expected: str
    retrieved: list
    hit_at_1: bool       # expected match is #1 result
    hit_at_3: bool       # expected match is in top 3
    difficulty: str
    retrieval_relevance: float
    faithfulness: float
    practicality: float
    overall: float
    reasoning: str

def clean_json_string(raw: str) -> str:
    """Removes markdown code blocks and whitespace."""
    raw = raw.strip()
    if raw.startswith("```"):
        # Matches ```json or just ```
        lines = raw.splitlines()
        content_lines = [line for line in lines if not line.strip().startswith("```")]
        return "".join(content_lines).strip()
    return raw


# ─── Step 1: Generate Test Dataset ────────────────────────
def generate_test_cases(recipes: list) -> list[TestCase]:
    recipe_summary = "\n".join([
        f"- {r['name']}: {', '.join([i['name'] for i in r.get('ingredients', [])])}"
        for r in recipes
    ])

    response = client.messages.create(
        model=EVAL_MODEL,
        max_tokens=2000,
        messages=[{
            "role": "user",
            "content": f"""
            You are creating a test dataset for a cooking RAG system evaluation.
            
            These recipes exist in the system:
            {recipe_summary}
            
            Generate {N_TEST_CASES} test cases split as:
            - 5 easy: ingredients that clearly map to one recipe
            - 5 medium: partial matches, 1-2 ingredients missing
            - 5 hard: ambiguous ingredients (e.g. "chicken" not "chicken breast"), 
                      different forms, or multiple possible matches
            
            Return ONLY a JSON array, no other text:
            [
              {{
                "available_ingredients": "ingredient1, ingredient2, ...",
                "expected_top_match": "Recipe Name",
                "difficulty": "easy|medium|hard"
              }}
            ]
            """
        }]
    )

    # raw = response.content[0].text.strip()
    # # NEW: Remove Markdown code block wrappers if they exist
    # if raw.startswith("```"):
    #     # Remove the first line (```json) and the last line (```)
    #     lines = raw.splitlines()
    #     if lines[0].startswith("```"):
    #         lines = lines[1:]
    #     if lines[-1].startswith("```"):
    #         lines = lines[:-1]
    #     raw = "\n".join(lines).strip()

    raw = clean_json_string(response.content[0].text.strip())

    try:
        data = json.loads(raw)
        return [TestCase(**tc) for tc in data]
    except json.JSONDecodeError as e:
        print(f"Failed to parse LLM response as JSON. Raw output was:\n{raw}")
        raise e 

# ─── Step 2: Call Your Pipeline ───────────────────────────
def call_pipeline(ingredients: str, headers: dict) -> dict:
    response = requests.post(
        f"{API_BASE}/api/suggest",
        headers=headers,
        json={"availableIngredients": ingredients},
        timeout=30
    )
    response.raise_for_status()
    return response.json()


# ─── Step 3: LLM Judge ────────────────────────────────────
def llm_judge(query: str, matches: list, suggestion: str) -> dict:
    retrieved_names = [r["name"] for r in matches]
    retrieved_ingredients = [
        f"{r['name']}: {', '.join([i['name'] for i in r.get('ingredients', [])])}"
        for r in matches
    ]

    response = client.messages.create(
        model=EVAL_MODEL,
        max_tokens=300,
        messages=[{
            "role": "user",
            "content": f"""
            Evaluate this cooking RAG system response.
            
            User has these ingredients: {query}
            
            System retrieved these recipes:
            {chr(10).join(retrieved_ingredients) if retrieved_ingredients else "None"}
            
            System generated this new recipe suggestion:
            {suggestion[:400] if suggestion else "None"}
            
            Score each dimension 1.0-5.0:
            - retrieval_relevance: are retrieved recipes relevant to available ingredients?
            - faithfulness: does generated recipe only use available ingredients?
            - practicality: could someone actually cook this with those ingredients?
            - overall: overall usefulness of the full response
            
            Return ONLY a valid JSON array. Do not include markdown formatting, backticks, or any introductory text. The response must start with '{{' and end with '}}'.
            {{
                "retrieval_relevance": 0.0,
                "faithfulness": 0.0,
                "practicality": 0.0,
                "overall": 0.0,
                "reasoning": "one sentence"
            }}
            """
        }]
    )
    raw = clean_json_string(response.content[0].text.strip())
    try:
        data = json.loads(raw)
        return data
    except json.JSONDecodeError as e:
        print(f"Failed to parse LLM response as JSON. Raw output was:\n{raw}")
        raise e 
    # return json.loads(response.content[0].text.strip())


# ─── Step 4: Evaluate Single Test Case ────────────────────
def evaluate_case(case: TestCase, headers: dict) -> EvalResult:
    result = call_pipeline(case.available_ingredients, headers)
    matches = result.get("matches", [])
    suggestion = result.get("newRecipeSuggestion", "")

    match_names = [r["name"].lower() for r in matches]
    expected = case.expected_top_match.lower()

    hit_at_1 = len(match_names) > 0 and (
        expected in match_names[0] or match_names[0] in expected
    )
    hit_at_3 = any(
        expected in name or name in expected
        for name in match_names
    )

    scores = llm_judge(case.available_ingredients, matches, suggestion)

    return EvalResult(
        query=case.available_ingredients,
        expected=case.expected_top_match,   
        retrieved=[r["name"] for r in matches],
        hit_at_1=hit_at_1,
        hit_at_3=hit_at_3,
        difficulty=case.difficulty,
        retrieval_relevance=scores["retrieval_relevance"],
        faithfulness=scores["faithfulness"],
        practicality=scores["practicality"],
        overall=scores["overall"],
        reasoning=scores["reasoning"]
    )


# ─── Step 5: Report ───────────────────────────────────────
def generate_report(results: list[EvalResult]) -> dict:
    total = len(results)

    def avg(field):
        return round(sum(getattr(r, field) for r in results) / total, 2)
    
    def hit_rate(subset, field):
        if not subset: return 0
        return round(sum(1 for r in subset if getattr(r, field)) / len(subset) * 100, 1)
    
    by_difficulty = {
        d: [r for r in results if r.difficulty == d]
        for d in ["easy", "medium", "hard"]
    }

    report = {
        "metadata": {
            "timestamp": datetime.now().isoformat(),
            "total_cases": total,
            "model_used": EVAL_MODEL,
            "api_base": API_BASE
        },
        "overall_metrics": {
            "hit_rate_at_1": f"{hit_rate(results, 'hit_at_1')}%",
            "hit_rate_at_3": f"{hit_rate(results, 'hit_at_3')}%",
            "avg_retrieval_relevance": avg("retrieval_relevance"),
            "avg_faithfulness": avg("faithfulness"),
            "avg_practicality": avg("practicality"),
            "avg_overall": avg("overall")
        },
        "by_difficulty": {
            d: {
                "count": len(group),
                "hit_rate_at_1": f"{hit_rate(group, 'hit_at_1')}%",
                "hit_rate_at_3": f"{hit_rate(group, 'hit_at_3')}%",
                "avg_overall": round(sum(r.overall for r in group) / len(group), 2) if group else 0
            }
            for d, group in by_difficulty.items()
        },
        "failed_cases": [
            {
                "query": r.query,
                "expected": r.expected,
                "retrieved": r.retrieved,
                "reasoning": r.reasoning
            }
            for r in results if not r.hit_at_3
        ],
        "all_results": [asdict(r) for r in results]
    }

    return report


# ─── Main ─────────────────────────────────────────────────
def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--token", required=True, help="JWT Token")
    parser.add_argument("--output", default="eval_results.json")
    args = parser.parse_args()

    headers = {
        "Authorization": f"Bearer {args.token}",
        "Content-Type": "application/json"
    }

    print("Step 1/4 — Fetching saved recipes from API...")
    recipes = requests.get(f"{API_BASE}/api/recipes", headers=headers).json()
    print(f"  Found {len(recipes)} recipes")

    print(f"Step 2/4 — Generating {N_TEST_CASES} synthetic test cases...")
    test_cases = generate_test_cases(recipes)
    print(f"  Generated {len(test_cases)} cases")

    print("Step 3/4 — Running evaluation...")
    results = []
    for i, case in enumerate(test_cases):
        print(f"  [{i+1}/{len(test_cases)}] {case.difficulty.upper()}: {case.available_ingredients[:60]}")
        try:
            result = evaluate_case(case, headers)
            results.append(result)
            print(f"    Hit@1: {result.hit_at_1} | Overall: {result.overall}/5 | {result.reasoning[:80]}")
        except Exception as e:
            print(f"    FAILED: {e}")

    print("Step 4/4 — Generating report...")
    report = generate_report(results)

    with open(args.output, "w") as f:
        json.dump(report, f, indent=2)


    # Print summary to console
    m = report["overall_metrics"]
    print("\n" + "="*50)
    print("FRIDGE TO FORK — EVALUATION SUMMARY")
    print("="*50)
    print(f"Hit Rate @1:          {m['hit_rate_at_1']}")
    print(f"Hit Rate @3:          {m['hit_rate_at_3']}")
    print(f"Retrieval Relevance:  {m['avg_retrieval_relevance']}/5")
    print(f"Faithfulness:         {m['avg_faithfulness']}/5")
    print(f"Practicality:         {m['avg_practicality']}/5")
    print(f"Overall Quality:      {m['avg_overall']}/5")
    print(f"\nFull results saved to: {args.output}")

if __name__ == "__main__":
    main()
