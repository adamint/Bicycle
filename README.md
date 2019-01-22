# Bicycle Templating Engine
Bicycle is a simple, intuitive templating engine modeled after Handlebars.js

## Why use Bicycle?
Bicycle is in active development, though it's already feature-rich. Some things you can do with Bicycle:
- Render Java/Kotlin classes and fields.
- Use built-in constructs like `{{#each}}` and `{{#equals}}`
- Decrease the amount of code you need to write with reusable templates
- Easily extend or modify the functionality of Bicycle by registering your own `Wheel`.

## How does it work?
Bicycle templates are compiled from a string into a fittingly-named `BicycleTemplate`,
which contains a list of text blocks and `Wheel` blocks (helper functions). When you 
render a Bicycle template, each block is recursively rendered using the provided model.

## Wheels
Whenever you want to add logic into your templates, you'll use a `Wheel`. 
There are two types: _variable_ block Wheels and _function_ block Wheels.

### Variable block Wheels
To render a field, class, or function, you use a variable block Wheel, which take the 
general form of `{{name *arguments *assignments}}`.

(continue..)