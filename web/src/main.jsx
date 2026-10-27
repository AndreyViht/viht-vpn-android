import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App';
import ErrorBoundary from './components/ErrorBoundary';
import { LayoutGroup, MotionConfig } from 'framer-motion';
import { springs } from './utils/motion';

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <ErrorBoundary>
      <MotionConfig reducedMotion="user" transition={springs.natural}>
        <LayoutGroup id="viht-liquid">
          <App />
        </LayoutGroup>
      </MotionConfig>
    </ErrorBoundary>
  </React.StrictMode>
);
