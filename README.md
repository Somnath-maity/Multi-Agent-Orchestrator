# Multi-Agent-Orchestrator-Pvt
The idea: a user submits a goal (e.g. "research X and draft a summary report"). A planner breaks it into a DAG of steps. A worker executes each step (LLM call or tool call) through Portkey, with schema validation, retries, caching, and response re-ranking. The client watches progress stream in.
