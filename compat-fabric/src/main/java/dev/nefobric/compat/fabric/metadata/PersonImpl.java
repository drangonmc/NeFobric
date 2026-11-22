package dev.nefobric.compat.fabric.metadata;

import net.fabricmc.loader.api.metadata.ContactInformation;
import net.fabricmc.loader.api.metadata.Person;

public final class PersonImpl implements Person {
    private final String name;
    private final ContactInformation contact;

    public PersonImpl(String name, ContactInformation contact) {
        this.name = name;
        this.contact = contact == null ? ContactInformation.EMPTY : contact;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public ContactInformation getContact() {
        return contact;
    }

    @Override
    public String toString() {
        return name;
    }
}
