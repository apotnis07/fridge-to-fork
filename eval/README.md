## RAG Evaluation

Offline evaluation pipeline for Fridge to Fork using LLM-as-a-Judge.

### Setup
```bash
cd eval
python -m venv venv
source venv/bin/activate
pip install -r requirements.txt
```

### Run
```bash
export ANTHROPIC_API_KEY=your_key_here

python fridge_to_fork_eval.py \
    --token "YOUR_JWT_TOKEN" \
    --output results/eval_$(date +%Y%m%d).json
```

### Metrics
- **Hit@1** — expected recipe is the top match
- **Hit@3** — expected recipe is in top 3 results
- **Retrieval Relevance** — are retrieved recipes relevant to query?
- **Faithfulness** — does generated recipe use only available ingredients?
- **Practicality** — could someone actually cook this?