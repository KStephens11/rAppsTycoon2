# Bugfix Requirements Document

## Introduction

When a player attempts to disable an active rApp deployment via the game UI, the action fails with an error. The root cause is an HTTP method mismatch: the frontend sends a `POST` request to the disable endpoint, but the backend controller expects a `PUT` request. This results in a `405 Method Not Allowed` response, which the frontend displays as a generic "Failed to disable rApp" error toast.

## Bug Analysis

### Current Behavior (Defect)

1.1 WHEN a player clicks the "Disable" button on an active rApp THEN the system sends a POST request to `/api/sessions/{code}/rapps/{id}/disable` and receives a 405 Method Not Allowed error

1.2 WHEN the 405 error is received from the disable request THEN the system displays a generic "Failed to disable rApp" error toast and the rApp remains in ACTIVE status

### Expected Behavior (Correct)

2.1 WHEN a player clicks the "Disable" button on an active rApp THEN the system SHALL send a PUT request to `/api/sessions/{code}/rapps/{id}/disable` matching the backend's expected HTTP method

2.2 WHEN the disable request succeeds THEN the system SHALL display a "rApp disabled" success toast and the rApp status SHALL transition to DISABLED

### Unchanged Behavior (Regression Prevention)

3.1 WHEN a player deploys an rApp via the deploy action THEN the system SHALL CONTINUE TO send a POST request to `/api/sessions/{code}/rapps/deploy` and create the deployment successfully

3.2 WHEN a player tunes an active rApp THEN the system SHALL CONTINUE TO send a PUT request to the tune endpoint and update the configuration successfully

3.3 WHEN a player rolls back an active rApp THEN the system SHALL CONTINUE TO handle the rollback request correctly (note: rollback currently has the same POST vs PUT mismatch and should also be fixed)

3.4 WHEN a player attempts to disable an rApp that is not in ACTIVE status THEN the system SHALL CONTINUE TO receive an appropriate INVALID_STATE error from the backend
