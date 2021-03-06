using System;

namespace Test.Lifetimes
{
    public class Disposable : IDisposable
    {
        private Action myAction;

        public Disposable(Action action)
        {
            myAction = action;
        }

        public void Dispose()
        {
            myAction?.Invoke();
        }

        public static IDisposable CreateAction(Action dispose) => new Disposable(dispose);
    }
}