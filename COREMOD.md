Using ModPatcher in your CoreMod
=====

Add the following annotation to your IFMLLoadingPlugin.

    @IFMLLoadingPlugin.SortingIndex(1001) // Magic value, after deobf transformer.

Change your IFMLLoadingPlugin's `getSetupClass(data)` method to:

    return me.nallar.modpatcher.ModPatcher.getSetupClass()

Load your patch files with ModPatcher in your IFMLLoadingPlugin's injectData method. For example:

    ModPatcher.addPatchesFromInputStream(MyClass.class.getResourceAsStream("/modpatcher.xml"));

See the [example coremod](https://github.com/nallar/ModPatcherExample) for more detail. 
