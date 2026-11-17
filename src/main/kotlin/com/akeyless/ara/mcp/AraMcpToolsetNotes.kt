package com.akeyless.ara.mcp

/**
 * Optional MCP toolset registration for JetBrains IDE MCP Server (`com.intellij.mcpServer`)
 * is planned for a follow-up release once the plugin targets IDEs that ship the stable
 * `mcpServer.mcpToolset` extension point (2025.2+).
 *
 * v1 exposes ARA through the **Akeyless ARA** tool window. Agents that speak MCP can still
 * use the companion npm connectors (`@akeyless-community/claude-connector` /
 * `@akeyless-community/cursor-connector`) via AI Assistant → MCP settings.
 *
 * When implemented, tools will mirror Claude/Cursor:
 * list-secrets, query-db, service-execute, list-sub-tools (including remote commands
 * on SSH, Windows, and LDAP items and SSH certificate issuers).
 */
object AraMcpToolsetNotes
