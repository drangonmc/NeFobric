package net.fabricmc.loader.api.metadata.version;

import dev.nefobric.compat.fabric.version.VersionPredicateParser;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

/** 版本条件，例如 {@code >=0.14.21 <0.17}。 */
public interface VersionPredicate extends Predicate<Version> {
    Collection<? extends PredicateTerm> getTerms();

    VersionInterval getInterval();

    interface PredicateTerm {
        VersionComparisonOperator getOperator();

        Version getReferenceVersion();
    }

    static VersionPredicate parse(String predicate) throws VersionParsingException {
        return VersionPredicateParser.parse(predicate);
    }

    static Collection<VersionPredicate> parse(Collection<String> predicates) throws VersionParsingException {
        if (predicates.isEmpty()) {
            return java.util.Collections.singleton(VersionPredicateParser.getAny());
        }
        List<VersionPredicate> ret = new ArrayList<>(predicates.size());
        for (String version : predicates) {
            ret.add(parse(version));
        }
        return ret;
    }
}
