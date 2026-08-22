from datetime import datetime, timezone
from pathlib import PurePosixPath
from typing import Dict, List, Optional, Set

from .models import Agent, AuditEvent, Decision, Permission, Resource, Token


class AgentOS:
    """Reference monitor for agent identity, scope, delegation and auditing."""

    def __init__(self) -> None:
        self.agents: Dict[str, Agent] = {}
        self.resources: Dict[str, Resource] = {}
        self.tokens: Dict[str, Token] = {}
        self.revoked_tokens: Set[str] = set()
        self.approved_tokens: Set[str] = set()
        self.audit_log: List[AuditEvent] = []

    def register_agent(self, agent: Agent) -> None:
        self.agents[agent.agent_id] = agent

    def register_resource(self, resource: Resource) -> None:
        self.resources[resource.resource_id] = resource

    def grant(self, token: Token) -> None:
        self._require_known_agent(token.issuer_agent_id)
        self._require_known_agent(token.subject_agent_id)
        self._require_known_resource(token.resource_id)
        self.tokens[token.token_id] = token

    def delegate(self, parent_token_id: str, child_agent_id: str,
                 permissions: Set[Permission], uses_remaining: Optional[int] = None) -> Token:
        parent = self._active_token(parent_token_id)
        self._require_known_agent(child_agent_id)
        if Permission.DELEGATE not in parent.permissions:
            raise PermissionError("parent token cannot delegate")
        if not permissions.issubset(parent.permissions - {Permission.DELEGATE}):
            raise PermissionError("delegation cannot increase authority")
        if parent.uses_remaining is not None and uses_remaining is not None:
            if uses_remaining > parent.uses_remaining:
                raise PermissionError("child quota exceeds parent quota")
        child = Token.issue(parent.subject_agent_id, child_agent_id, parent.resource_id,
                            frozenset(permissions), parent.expires_at, uses_remaining)
        self.tokens[child.token_id] = child
        return child

    def revoke(self, token_id: str) -> None:
        self._active_token(token_id)
        self.revoked_tokens.add(token_id)

    def approve(self, token_id: str) -> None:
        self._active_token(token_id)
        self.approved_tokens.add(token_id)

    def check(self, agent_id: str, resource_id: str, operation: Permission,
              token_id: Optional[str] = None) -> Decision:
        reason = "allowed"
        decision = Decision.DENY
        try:
            agent = self._require_known_agent(agent_id)
            resource = self._require_known_resource(resource_id)
            if not self._within_scope(agent.resource_root, resource.path):
                raise PermissionError("resource is outside agent scope")
            token = self._active_token(token_id) if token_id else None
            if token and (token.subject_agent_id != agent_id or token.resource_id != resource_id):
                raise PermissionError("token subject or resource mismatch")
            if not token or operation not in token.permissions:
                raise PermissionError("permission not granted")
            if operation in {Permission.DELETE, Permission.SEND, Permission.DEPLOY} and token_id not in self.approved_tokens:
                decision = Decision.REQUIRE_HUMAN
                reason = "human approval required"
            else:
                decision = Decision.ALLOW
        except (KeyError, PermissionError, ValueError) as error:
            reason = str(error)
        owner_id = self.agents.get(agent_id, Agent(agent_id, "unknown")).owner_id
        self.audit_log.append(AuditEvent(datetime.now(timezone.utc), owner_id, agent_id,
                                         resource_id, operation, decision, reason, token_id))
        return decision

    @staticmethod
    def _within_scope(root: str, path: str) -> bool:
        try:
            PurePosixPath(path).relative_to(PurePosixPath(root))
            return True
        except ValueError:
            return False

    def _active_token(self, token_id: Optional[str]) -> Token:
        if not token_id or token_id not in self.tokens:
            raise PermissionError("unknown token")
        token = self.tokens[token_id]
        if token.revoked or token_id in self.revoked_tokens:
            raise PermissionError("token revoked")
        if token.expires_at and token.expires_at <= datetime.now(timezone.utc):
            raise PermissionError("token expired")
        return token

    def _require_known_agent(self, agent_id: str) -> Agent:
        if agent_id not in self.agents:
            raise ValueError("unknown agent")
        return self.agents[agent_id]

    def _require_known_resource(self, resource_id: str) -> Resource:
        if resource_id not in self.resources:
            raise ValueError("unknown resource")
        return self.resources[resource_id]