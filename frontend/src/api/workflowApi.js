const API_BASE = '/api';

/**
 * Submit a new workflow with the given goal.
 * POST /api/workflows
 */
export async function submitWorkflow(goal) {
  const response = await fetch(`${API_BASE}/workflows`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ goal }),
  });

  if (!response.ok) {
    const text = await response.text().catch(() => '');
    throw new Error(text || `Failed to submit workflow (${response.status})`);
  }

  return response.json();
}

/**
 * Fetch the current state of a workflow by ID.
 * GET /api/workflows/{id}
 */
export async function getWorkflow(id) {
  const response = await fetch(`${API_BASE}/workflows/${id}`);

  if (!response.ok) {
    throw new Error(`Failed to fetch workflow (${response.status})`);
  }

  return response.json();
}

/**
 * Open an SSE stream for real-time workflow events.
 * GET /api/workflows/{id}/events
 *
 * @param {string} id        - Workflow ID
 * @param {function} onEvent - Called with parsed event data for each message
 * @param {function} onError - Called on stream error
 * @returns {EventSource}    - The EventSource instance (caller can close it)
 */
export function streamWorkflowEvents(id, onEvent, onError) {
  const url = `${API_BASE}/workflows/${id}/events`;
  const eventSource = new EventSource(url);

  eventSource.onmessage = (event) => {
    try {
      const data = JSON.parse(event.data);
      onEvent(data);
    } catch {
      // If the data is not JSON, pass it as-is
      onEvent({ raw: event.data });
    }
  };

  eventSource.onerror = (event) => {
    if (eventSource.readyState === EventSource.CLOSED) {
      return;
    }
    if (onError) {
      onError(event);
    }
  };

  return eventSource;
}
