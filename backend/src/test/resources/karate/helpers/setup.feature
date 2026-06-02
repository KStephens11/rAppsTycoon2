# Reusable helper: creates a session with 2 players and starts the game.
# Exposes: sessionCode, hostToken, guestToken, hostPlayerId, guestPlayerId
# After start, basestationId is the first basestation of the host player.

@ignore
Feature: Game setup helper

  @createSession
  Scenario: Create session only
    Given url baseUrl
    And path '/api/sessions'
    And request { hostName: 'HostPlayer' }
    When method POST
    Then status 201
    * def sessionCode = response.sessionCode
    * def hostToken = response.hostPlayer.sessionToken
    * def hostPlayerId = response.hostPlayer.id

  @startGame
  Scenario: Create session, add guest, start game
    Given url baseUrl
    And path '/api/sessions'
    And request { hostName: 'HostPlayer' }
    When method POST
    Then status 201
    * def sessionCode = response.sessionCode
    * def hostToken = response.hostPlayer.sessionToken
    * def hostPlayerId = response.hostPlayer.id

    Given url baseUrl
    And path '/api/sessions/' + sessionCode + '/join'
    And request { displayName: 'GuestPlayer' }
    When method POST
    Then status 200
    * def guestToken = response.player.sessionToken
    * def guestPlayerId = response.player.id

    Given url baseUrl
    And path '/api/sessions/' + sessionCode + '/start'
    And header X-Session-Token = hostToken
    When method POST
    Then status 200

    Given url baseUrl
    And path '/api/sessions/' + sessionCode + '/basestations'
    And header X-Session-Token = hostToken
    When method GET
    Then status 200
    * def basestationId = response.basestations[0].id
    * def guestBasestationId = '#notnull'
