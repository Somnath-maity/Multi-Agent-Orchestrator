import { useState, useEffect, useCallback, useRef } from 'react';
import { getWorkflow, streamWorkflowEvents } from '../api/workflowApi.js';

const TERMINAL_STATUSES = ['SUCCEEDED', 'FAILED'];

function WorkflowProgress({ workflowId, onReset }) {
  const [workflow, setWorkflow] = useState(null);
  const [error, setError] = useState(null);
  const [expandedSteps, setExpandedSteps] = useState(new Set());
  const eventSourceRef = useRef(null);

  // Merge an incoming event into the workflow state
  const applyEvent = useCallback((event) => {
    setWorkflow((prev) => {
      if (!prev) return prev;

      // Handle workflow-level status updates
      if (event.workflowStatus) {
        return { ...prev, status: event.workflowStatus };
      }

      // Handle step-level updates
      if (event.stepIndex !== undefined && prev.steps) {
        const updatedSteps = [...prev.steps];
        const idx = event.stepIndex;
        if (idx >= 0 && idx < updatedSteps.length) {
          updatedSteps[idx] = {
            ...updatedSteps[idx],
            ...event.stepUpdate,
          };
        }
        return { ...prev, steps: updatedSteps };
      }

      // Handle full-state refresh events
      if (event.id && event.steps) {
        return event;
      }

      return prev;
    });
  }, []);

  // Fetch initial state and open SSE stream
  useEffect(() => {
    if (!workflowId) return;

    let cancelled = false;

    async function init() {
      try {
        const data = await getWorkflow(workflowId);
        if (cancelled) return;
        setWorkflow(data);
        setError(null);

        // Only open SSE if workflow is not yet terminal
        if (!TERMINAL_STATUSES.includes(data.status)) {
          const es = streamWorkflowEvents(
            workflowId,
            (event) => {
              if (!cancelled) applyEvent(event);
            },
            () => {
              if (!cancelled) {
                // On error, try to re-fetch the latest state
                getWorkflow(workflowId)
                  .then((refreshed) => {
                    if (!cancelled) setWorkflow(refreshed);
                  })
                  .catch(() => {});
              }
            },
          );
          eventSourceRef.current = es;
        }
      } catch (err) {
        if (!cancelled) {
          setError(err.message || 'Failed to load workflow');
        }
      }
    }

    init();

    return () => {
      cancelled = true;
      if (eventSourceRef.current) {
        eventSourceRef.current.close();
        eventSourceRef.current = null;
      }
    };
  }, [workflowId, applyEvent]);

  // Close SSE when workflow reaches terminal status
  useEffect(() => {
    if (workflow && TERMINAL_STATUSES.includes(workflow.status)) {
      if (eventSourceRef.current) {
        eventSourceRef.current.close();
        eventSourceRef.current = null;
      }
    }
  }, [workflow?.status]);

  const toggleStep = useCallback((index) => {
    setExpandedSteps((prev) => {
      const next = new Set(prev);
      if (next.has(index)) {
        next.delete(index);
      } else {
        next.add(index);
      }
      return next;
    });
  }, []);

  if (error) {
    return (
      <section className="card">
        <p className="error-message">{error}</p>
      </section>
    );
  }

  if (!workflow) {
    return (
      <section className="card">
        <span className="loading-spinner" /> Loading workflow...
      </section>
    );
  }

  const steps = workflow.steps || [];
  const completedCount = steps.filter(
    (s) => s.status === 'SUCCEEDED' || s.status === 'FAILED' || s.status === 'SKIPPED',
  ).length;
  const progressPercent = steps.length > 0 ? (completedCount / steps.length) * 100 : 0;

  const progressClass = workflow.status === 'FAILED'
    ? 'failed'
    : workflow.status === 'SUCCEEDED'
      ? 'succeeded'
      : '';

  return (
    <section className="card">
      <div className="workflow-meta">
        <span className="workflow-goal">{workflow.goal}</span>
        <span className={`badge badge-${workflow.status?.toLowerCase()}`}>
          {workflow.status}
        </span>
      </div>

      <div className="progress-bar-container">
        <div
          className={`progress-bar ${progressClass}`}
          style={{ width: `${progressPercent}%` }}
        />
      </div>

      {steps.length > 0 && (
        <ul className="steps-list">
          {steps.map((step, index) => {
            const isExpanded = expandedSteps.has(index);
            const statusClass = step.status?.toLowerCase() || '';
            const output = step.output || step.error;

            return (
              <li key={index} className={`step-item ${statusClass}`}>
                <div className="step-header" onClick={() => toggleStep(index)}>
                  <span className={`step-expand ${isExpanded ? 'open' : ''}`}>
                    &#9654;
                  </span>
                  {step.type && <span className="step-type">{step.type}</span>}
                  <span className="step-name">
                    {step.description || step.name || `Step ${index + 1}`}
                  </span>
                  {step.attemptCount > 1 && (
                    <span className="step-attempts">
                      attempt {step.attemptCount}
                    </span>
                  )}
                  <span className={`badge badge-${statusClass}`}>
                    {step.status}
                  </span>
                </div>

                {isExpanded && output && (
                  <div className="step-output">
                    {typeof output === 'string' ? output : JSON.stringify(output, null, 2)}
                  </div>
                )}
              </li>
            );
          })}
        </ul>
      )}

      {TERMINAL_STATUSES.includes(workflow.status) && (
        <div className="workflow-actions">
          <button className="btn btn-secondary" onClick={onReset}>
            New Workflow
          </button>
        </div>
      )}
    </section>
  );
}

export default WorkflowProgress;
