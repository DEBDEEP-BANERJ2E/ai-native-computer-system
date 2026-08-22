from dataclasses import dataclass, field
from datetime import datetime
from enum import Enum
from typing import FrozenSet, Optional
from uuid import uuid4


class Permission(str, Enum):
    READ = "READ"
    WRITE = "WRITE"
    CREATE = "CREATE"
    DELETE = "DELETE"
    EXECUTE = "EXECUTE"
    SEND = "SEND"
    DEPLOY = "DEPLOY"
    APPROVE = "APPROVE"
    DELEGATE = "DELEGATE"


class Decision(str, Enum):
    ALLOW = "allow"
    DENY = "deny"
    REQUIRE_HUMAN = "require-human"


@dataclass(frozen=True)
class Agent:
    agent_id: str
    owner_id: str
    parent_agent_id: Optional[str] = None
    session_id: str = field(default_factory=lambda: str(uuid4()))
    resource_root: str = "/"


@dataclass(frozen=True)
class Resource:
    resource_id: str
    resource_type: str
    owner_id: str
    path: str


@dataclass(frozen=True)
class Token:
    token_id: str
    issuer_agent_id: str
    subject_agent_id: str
    resource_id: str
    permissions: FrozenSet[Permission]
    expires_at: Optional[datetime] = None
    uses_remaining: Optional[int] = None
    revoked: bool = False

    @classmethod
    def issue(cls, issuer_agent_id: str, subject_agent_id: str, resource_id: str,
              permissions: FrozenSet[Permission], expires_at: Optional[datetime] = None,
              uses_remaining: Optional[int] = None) -> "Token":
        return cls(str(uuid4()), issuer_agent_id, subject_agent_id, resource_id,
                   permissions, expires_at, uses_remaining)


@dataclass(frozen=True)
class AuditEvent:
    timestamp: datetime
    principal_id: str
    agent_id: str
    resource_id: str
    operation: Permission
    decision: Decision
    reason: str
    token_id: Optional[str] = None