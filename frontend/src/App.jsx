import { useState, useCallback } from 'react';
import WorkflowSubmit from './components/WorkflowSubmit.jsx';
import WorkflowProgress from './components/WorkflowProgress.jsx';

function App() {
  const [currentWorkflowId, setCurrentWorkflowId] = useState(null);

  const handleWorkflowCreated = useCallback((response) => {
    setCurrentWorkflowId(response.id);
  }, []);

  const handleReset = useCallback(() => {
    setCurrentWorkflowId(null);
  }, []);

  return (
    <div className="app">
      <header className="app-header">
        <h1>Multi-Agent Workflow Orchestrator</h1>
        <p className="app-subtitle">Submit a goal and watch agents collaborate in real-time</p>
      </header>

      <main className="app-main">
        <WorkflowSubmit
          onWorkflowCreated={handleWorkflowCreated}
          disabled={currentWorkflowId !== null}
        />

        {currentWorkflowId && (
          <WorkflowProgress
            workflowId={currentWorkflowId}
            onReset={handleReset}
          />
        )}
      </main>
    </div>
  );
}

export default App;
