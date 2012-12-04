zkfields
========

The _ZkFields plugin_ allows you to very quickly render forms for properties of domain objects, command beans and POGOs based on their type, name, etc. The plugin aims to:

* Use good defaults for fields.
* Support embedded properties of _GORM_ domain classes.


Usage
-----

The _zkf:field_ tag will automatically handle embedded domain properties recursively:

```xml
<zkf:field bean="person" property="address"/>
```


To make it more convenient when rendering lots of properties of the same _bean_ you can use the _zkf:with_ tag to avoid having to specify _bean_ on any tags nested inside:

```xml
<zkf:with bean="person" viewmode="edit">
	<zkf:field property="name"/>
	<zkf:field property="address"/>
	<zkf:field property="dateOfBirth"/>
</zkf:with>
```

the _zkf:with_ tag accepts an optional argument: viewmode. It accepts two values: _edit_ and _readonly_. The default is _edit_. If _readonly_ is set then all fields will be rendered as labels instead of inputs

Changelog
---------

### Version 0.1.0

2012-12-04: Initial releaseorm-field rendering based on zkui
