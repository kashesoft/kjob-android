/*
 * Copyright (C) 2016 Andrey Kashaed
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.kashesoft.kjob;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

public class Action<O> {

    private ActableO<O> actable;

    public Action(ActableO<O> actable) {
        this.actable = actable;
    }

    @SuppressWarnings("unchecked")
    void act(Object object) throws Exception {
        if (parameterClass().isInstance(object)) {
            actable.act((O) object);
        }
    }

    @SuppressWarnings ("unchecked")
    private Class<O> parameterClass() {
        ParameterizedType interfaceType = (ParameterizedType) actable.getClass().getGenericInterfaces()[0];
        Type parameterType = interfaceType.getActualTypeArguments()[0];
        return (Class<O>) parameterType;
    }

}
