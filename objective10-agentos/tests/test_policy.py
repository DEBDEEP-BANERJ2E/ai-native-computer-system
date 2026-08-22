import unittest

from agentos import Agent, AgentOS, Decision, Permission, Resource, Token


class AgentOSTest(unittest.TestCase):
    def setUp(self):
        self.os = AgentOS()
        self.os.register_agent(Agent("system", "system"))
        self.os.register_agent(Agent("manager", "user", resource_root="/repo"))
        self.os.register_agent(Agent("coder", "user", parent_agent_id="manager", resource_root="/repo"))
        self.os.register_resource(Resource("source", "file", "user", "/repo/app.py"))
        self.manager_token = Token.issue("system", "manager", "source",
                                         frozenset({Permission.READ, Permission.DELEGATE, Permission.DELETE}))
        self.os.grant(self.manager_token)

    def test_scope_and_permission_are_enforced(self):
        self.assertEqual(self.os.check("manager", "source", Permission.READ, self.manager_token.token_id), Decision.ALLOW)
        self.assertEqual(self.os.check("manager", "source", Permission.WRITE, self.manager_token.token_id), Decision.DENY)

    def test_delegation_is_attenuated(self):
        child = self.os.delegate(self.manager_token.token_id, "coder", {Permission.READ})
        self.assertEqual(self.os.check("coder", "source", Permission.READ, child.token_id), Decision.ALLOW)
        with self.assertRaises(PermissionError):
            self.os.delegate(self.manager_token.token_id, "coder", {Permission.DELETE, Permission.DELEGATE})

    def test_dangerous_action_requires_approval_and_revocation_is_immediate(self):
        self.assertEqual(self.os.check("manager", "source", Permission.DELETE, self.manager_token.token_id), Decision.REQUIRE_HUMAN)
        self.os.approve(self.manager_token.token_id)
        self.assertEqual(self.os.check("manager", "source", Permission.DELETE, self.manager_token.token_id), Decision.ALLOW)
        self.os.revoke(self.manager_token.token_id)
        self.assertEqual(self.os.check("manager", "source", Permission.DELETE, self.manager_token.token_id), Decision.DENY)


if __name__ == "__main__":
    unittest.main()