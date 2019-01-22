## Example template compilation

The following template model:
```html
<h3 class="light uk-margin-remove-bottom">Hi, I'm {{name}}. I'm a linguistics and programming enthusiast.</h3>
{{#each model}}
    {{& data one=true two="hello world"}}
{{/each}}

{{> "test-template.bike"}}

{{#equals false false}}test{{/equals}}

{{#if condition}}
    <p>Condition true<p>
    {{condition}}
    {{else}}
        <p>Condition false</p>
{{/if}}
        {{#not condition}}
        <p>Condition false</p>
        {{/not}}
```

would be compiled in the following model:

```json
{
    "engine": {},
    "parts": [
        {
            "text": "\u003ch3 class\u003d\"light uk-margin-remove-bottom\"\u003eHi, I\u0027m "
        },
        {
            "engine": {},
            "wheel": {
                "name": "",
                "possibleArguments": [
                    {
                        "name": "value",
                        "takes": [
                            "OBJECT"
                        ],
                        "nullable": true
                    },
                    {
                        "name": "show-null",
                        "takes": [
                            "BOOLEAN"
                        ],
                        "nullable": true
                    }
                ]
            },
            "arguments": {
                "value": "name"
            },
            "setVariables": {}
        },
        {
            "text": ". I\u0027m a linguistics and programming enthusiast.\u003c/h3\u003e\r\n"
        },
        {
            "engine": {},
            "wheel": {
                "name": "each",
                "possibleArguments": [
                    {
                        "name": "value",
                        "takes": [
                            "LIST"
                        ],
                        "nullable": true
                    }
                ]
            },
            "innerTemplate": {
                "engine": {},
                "parts": [
                    {
                        "text": "    "
                    },
                    {
                        "engine": {},
                        "wheel": {
                            "name": "",
                            "possibleArguments": [
                                {
                                    "name": "value",
                                    "takes": [
                                        "OBJECT"
                                    ],
                                    "nullable": true
                                },
                                {
                                    "name": "show-null",
                                    "takes": [
                                        "BOOLEAN"
                                    ],
                                    "nullable": true
                                }
                            ]
                        },
                        "arguments": {
                            "value": "data"
                        },
                        "setVariables": {
                            "noescape": true,
                            "two": "\"hello world\"",
                            "one": true
                        }
                    },
                    {
                        "text": ""
                    }
                ]
            },
            "arguments": {
                "value": "model"
            },
            "setVariables": {}
        },
        {
            "text": "\r\n\r\n"
        },
        {
            "engine": {},
            "wheel": {
                "name": "template-resolver",
                "possibleArguments": [
                    {
                        "name": "value",
                        "takes": [
                            "OBJECT"
                        ],
                        "nullable": false
                    }
                ]
            },
            "arguments": {
                "value": "\"test-template.bike\""
            },
            "setVariables": {}
        },
        {
            "text": "\r\n\r\n"
        },
        {
            "engine": {},
            "wheel": {
                "name": "equals",
                "possibleArguments": [
                    {
                        "name": "first",
                        "takes": [
                            "OBJECT"
                        ],
                        "nullable": true
                    },
                    {
                        "name": "second",
                        "takes": [
                            "OBJECT"
                        ],
                        "nullable": true
                    }
                ]
            },
            "innerTemplate": {
                "engine": {},
                "parts": [
                    {
                        "text": "test"
                    }
                ]
            },
            "arguments": {
                "first": false,
                "second": false
            },
            "setVariables": {}
        },
        {
            "text": "\r\n\r\n"
        },
        {
            "engine": {},
            "wheel": {
                "name": "if",
                "possibleArguments": [
                    {
                        "name": "value",
                        "takes": [
                            "BOOLEAN"
                        ],
                        "nullable": true
                    }
                ]
            },
            "innerTemplate": {
                "engine": {},
                "parts": [
                    {
                        "text": "    \u003cp\u003eCondition true\u003cp\u003e\r\n    "
                    },
                    {
                        "engine": {},
                        "wheel": {
                            "name": "",
                            "possibleArguments": [
                                {
                                    "name": "value",
                                    "takes": [
                                        "OBJECT"
                                    ],
                                    "nullable": true
                                },
                                {
                                    "name": "show-null",
                                    "takes": [
                                        "BOOLEAN"
                                    ],
                                    "nullable": true
                                }
                            ]
                        },
                        "arguments": {
                            "value": "condition"
                        },
                        "setVariables": {}
                    },
                    {
                        "text": "\r\n    "
                    }
                ]
            },
            "arguments": {
                "value": "condition"
            },
            "setVariables": {}
        },
        {
            "engine": {},
            "wheel": {
                "name": "not",
                "possibleArguments": [
                    {
                        "name": "value",
                        "takes": [
                            "BOOLEAN"
                        ],
                        "nullable": true
                    }
                ]
            },
            "innerTemplate": {
                "engine": {},
                "parts": [
                    {
                        "engine": {},
                        "wheel": {
                            "name": "not",
                            "possibleArguments": [
                                {
                                    "name": "value",
                                    "takes": [
                                        "BOOLEAN"
                                    ],
                                    "nullable": true
                                }
                            ]
                        },
                        "innerTemplate": {
                            "engine": {},
                            "parts": [
                                {
                                    "text": "        \u003cp\u003eCondition false\u003c/p\u003e\n"
                                }
                            ]
                        },
                        "arguments": {
                            "value": "condition"
                        },
                        "setVariables": {}
                    },
                    {
                        "text": ""
                    }
                ]
            },
            "arguments": {
                "value": "condition"
            },
            "setVariables": {}
        },
        {
            "text": "\r\n        "
        },
        {
            "engine": {},
            "wheel": {
                "name": "not",
                "possibleArguments": [
                    {
                        "name": "value",
                        "takes": [
                            "BOOLEAN"
                        ],
                        "nullable": true
                    }
                ]
            },
            "innerTemplate": {
                "engine": {},
                "parts": [
                    {
                        "text": "        \u003cp\u003eCondition false\u003c/p\u003e\r\n        "
                    }
                ]
            },
            "arguments": {
                "value": "condition"
            },
            "setVariables": {}
        },
        {
            "text": "\r\n"
        }
    ]
}
```