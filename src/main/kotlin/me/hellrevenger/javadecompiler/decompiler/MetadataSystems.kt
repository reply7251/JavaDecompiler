package me.hellrevenger.javadecompiler.decompiler

import com.strobel.assembler.metadata.ITypeLoader
import com.strobel.assembler.metadata.MetadataSystem
import com.strobel.assembler.metadata.TypeDefinition

class NoRetryMetadataSystem(typeLoader: ITypeLoader) : MetadataSystem(typeLoader) {
    val fails = hashSetOf<String>()
    override fun resolveType(descriptor: String, mightBePrimitive: Boolean): TypeDefinition? {
        if(descriptor in fails) return null
        val result = super.resolveType(descriptor, mightBePrimitive)
        if(result == null)
            fails.add(descriptor)
        return result
    }
}