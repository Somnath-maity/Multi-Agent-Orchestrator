import { useState, useCallback } from 'react';
import { submitWorkflow } from '../api/workflowApi.js';

function WorkflowSubmit({ onWorkflowCreated, disabled }) {
  const [goal, setGoal] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const handleSubmit = useCallback(
    async (e) => {
      e.preventDefault();
      const trimmed = goal.trim();
      if (!trimmed) return;

      setLoading(true);
      setError(null);

      try {
        const response = await submitWorkflow(trimmed);
        setGoal('');
        onWorkflowCreated(response);
      } catch (err) {
        setError(err.message || 'Failed to submit workflow');
      } finally {
        setLoading(false);
      }
    },
    [goal, onWorkflowCreated],
  );

  return (
    <section className="card">
      <h2 className="card-title">New Workflow</h2>
      <form className="workflow-form" onSubmit={handleSubmit}>
        <input
          className="workflow-input"
          type="text"
          placeholder="Describe your workflow goal..."
          value={goal}
          onChange={(e) => setGoal(e.target.value)}
          disabled={loading || disabled}
          autoFocus
        />
        <button
          className="btn btn-primary"
          type="submit"
          disabled={loading || disabled || !goal.trim()}
        >
          {loading ? (
            <>
              <span className="loading-spinner" />
              Submitting...
            </>
          ) : (
            'Submit'
          )}
        </button>
      </form>
      {error && <p className="error-message">{error}</p>}
    </section>
  );
}

export default WorkflowSubmit;
